"""One decode of the upload, then the tensors the runners expect."""

from __future__ import annotations

import io
from dataclasses import dataclass

import numpy as np


INPUT_SIZE = 384
SEVERITY_LONG_SIDE = 512
_MAX_PIXELS = 24_000_000
_IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
_JPEG = b"\xff\xd8\xff"
_PNG = b"\x89PNG\r\n\x1a\n"


@dataclass(eq=False)
class PreparedImage:
    nchw: np.ndarray
    rgb_uint8: np.ndarray
    lesion_ratio: float

    @classmethod
    def blank(cls) -> PreparedImage:
        rgb = np.zeros((INPUT_SIZE, INPUT_SIZE, 3), dtype=np.uint8)
        return cls(nchw=_imagenet_nchw(rgb), rgb_uint8=rgb, lesion_ratio=0.0)

    @classmethod
    def from_bytes(cls, data: bytes) -> PreparedImage:
        if not isinstance(data, (bytes, bytearray)) or not (
            data.startswith(_JPEG) or data.startswith(_PNG)
        ):
            raise ValueError("image must be jpeg or png")
        from PIL import Image
        from PIL import UnidentifiedImageError

        Image.MAX_IMAGE_PIXELS = _MAX_PIXELS
        resample = getattr(Image, "Resampling", Image).BILINEAR
        try:
            with Image.open(io.BytesIO(data)) as image:
                rgb = image.convert("RGB")
                severity = _resize_long_side(rgb, SEVERITY_LONG_SIDE, resample)
                ratio = _lesion_ratio(np.asarray(severity))
                network = rgb.resize((INPUT_SIZE, INPUT_SIZE), resample)
                rgb_uint8 = np.ascontiguousarray(np.asarray(network, dtype=np.uint8))
        except (UnidentifiedImageError, OSError) as exc:
            raise ValueError("could not read image") from exc
        if rgb_uint8.shape != (INPUT_SIZE, INPUT_SIZE, 3):
            raise ValueError("could not read image")
        return cls(
            nchw=_imagenet_nchw(rgb_uint8),
            rgb_uint8=rgb_uint8,
            lesion_ratio=float(ratio),
        )


@dataclass(eq=False)
class VisionPrediction:
    label_id: str
    confidence: float
    severity: str
    lesion_ratio: float
    model_id: str
    model_version: str
    backend: str


def softmax(logits: np.ndarray) -> np.ndarray:
    scores = np.asarray(logits, dtype=np.float32).reshape(-1)
    if scores.size == 0:
        raise ValueError("empty logits")
    shifted = scores - np.max(scores)
    exp = np.exp(shifted)
    total = float(np.sum(exp))
    if total == 0.0 or not np.isfinite(total):
        raise ValueError("softmax failed")
    return exp / total


def _imagenet_nchw(rgb_uint8: np.ndarray) -> np.ndarray:
    image = rgb_uint8.astype(np.float32) / 255.0
    image = (image - _IMAGENET_MEAN) / _IMAGENET_STD
    chw = np.transpose(image, (2, 0, 1))
    return np.ascontiguousarray(chw[None, ...], dtype=np.float32)


def _resize_long_side(image, long_side: int, resample):
    width, height = image.size
    scale = long_side / float(max(width, height))
    resized = (
        max(1, int(round(width * scale))),
        max(1, int(round(height * scale))),
    )
    return image.resize(resized, resample)


def _lesion_ratio(rgb: np.ndarray) -> float:
    """Share of plant pixels that are not green leaf tissue.

    Near-white and near-black backgrounds are left out of the count. Pests do
    not use this number; the runners pass it only for disease classes.
    """

    pixels = np.asarray(rgb, dtype=np.float32)
    red = pixels[:, :, 0]
    green = pixels[:, :, 1]
    blue = pixels[:, :, 2]
    brightest = np.maximum(np.maximum(red, green), blue)
    darkest = np.minimum(np.minimum(red, green), blue)
    saturation = (brightest - darkest) / np.maximum(brightest, 1.0)
    plant = (saturation >= 0.12) & (brightest >= 35.0) & (brightest <= 245.0)
    healthy = plant & (green >= red + 8.0) & (green >= blue + 8.0)
    plant_count = int(plant.sum())
    if plant_count < 32:
        return 0.0
    lesions = int((plant & ~healthy).sum())
    return lesions / float(plant_count)
