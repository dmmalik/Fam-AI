"""EfficientNetV2-S int8 on the Pi Arm CPU through ExecuTorch.

The .pte must accept a float32 NCHW tensor of shape 1x3x384x384, ImageNet
normalized, and return class logits in kb/labels.json order. This module never
loads a Hailo HEF.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import numpy as np

from farm_ai.config import MODEL_ID
from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.vision.severity import severity_for
from farm_ai.vision.types import PreparedImage, VisionPrediction, softmax


class CpuExecuTorchVision:
    name = "cpu"
    model_id = MODEL_ID

    def __init__(self, model_path: Path, kb: KnowledgeBase) -> None:
        if not model_path.is_file():
            raise FileNotFoundError(f"classifier .pte not found: {model_path}")
        self.model_version = hashlib.sha256(model_path.read_bytes()).hexdigest()[:16]
        self._labels = kb.labels
        self._method = _load_method(model_path)

    def warm(self) -> None:
        self.predict(PreparedImage.blank(), None)

    def predict(self, image: PreparedImage, crop: str | None) -> VisionPrediction:
        del crop
        logits = _execute(self._method, image.nchw)
        if logits.shape[0] != len(self._labels):
            raise RuntimeError(
                f"classifier returned {logits.shape[0]} scores for {len(self._labels)} labels"
            )
        probs = softmax(logits.astype(np.float32))
        index = int(np.argmax(probs))
        label = self._labels[index]
        confidence = float(probs[index])
        severity = "low" if label["id"].endswith("_healthy") else severity_for(
            label["kind"], image.lesion_ratio, confidence
        )
        return VisionPrediction(
            label_id=label["id"],
            confidence=round(confidence, 4),
            severity=severity,
            lesion_ratio=round(image.lesion_ratio, 4),
            model_id=self.model_id,
            model_version=self.model_version,
            backend=self.name,
        )


def _load_method(path: Path):
    try:
        from executorch.runtime import Runtime
    except ImportError as exc:
        raise RuntimeError("ExecuTorch runtime is not installed on this device") from exc
    program = Runtime.get().load_program(str(path))
    return program.load_method("forward")


def _execute(method, nchw: np.ndarray) -> np.ndarray:
    try:
        import torch

        outputs = method.execute((torch.from_numpy(np.ascontiguousarray(nchw)),))
    except TypeError:
        outputs = method.execute([np.ascontiguousarray(nchw)])
    return np.array(outputs[0]).reshape(-1)
