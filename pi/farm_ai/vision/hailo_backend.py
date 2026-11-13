"""EfficientNetV2-S on the Hailo NPU through HailoRT.

Preprocess and severity stay on the Arm CPU. Llama is not loaded here.
The HEF must emit logits in the same order as kb/labels.json. Input is uint8
NHWC 384x384 RGB, which is the usual Hailo compile setting for this model.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import numpy as np

from farm_ai.config import MODEL_ID
from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.vision.severity import severity_for
from farm_ai.vision.types import PreparedImage, VisionPrediction, softmax


def hailo_runtime_available() -> bool:
    try:
        import hailo_platform  # noqa: F401
    except ImportError:
        return False
    return True


class HailoVision:
    name = "hailo"
    model_id = MODEL_ID

    def __init__(self, hef_path: Path, kb: KnowledgeBase) -> None:
        if not hef_path.is_file():
            raise FileNotFoundError(f"Hailo HEF not found: {hef_path}")
        if not hailo_runtime_available():
            raise RuntimeError("HailoRT is not installed")
        from hailo_platform import VDevice

        self.model_version = hashlib.sha256(hef_path.read_bytes()).hexdigest()[:16]
        self._labels = kb.labels
        self._device = VDevice()
        self._model = self._device.create_infer_model(str(hef_path))
        self._configured = self._model.configure()
        self._configured.__enter__()

    def warm(self) -> None:
        self.predict(PreparedImage.blank(), None)

    def predict(self, image: PreparedImage, crop: str | None) -> VisionPrediction:
        del crop
        bindings = self._configured.create_bindings()
        bindings.input().set_buffer(np.ascontiguousarray(image.rgb_uint8))
        self._configured.run([bindings], 1000)
        logits = np.array(bindings.output().get_buffer()).reshape(-1).astype(np.float32)
        if logits.shape[0] != len(self._labels):
            raise RuntimeError(
                f"HEF returned {logits.shape[0]} scores for {len(self._labels)} labels"
            )
        probs = softmax(logits)
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

    def close(self) -> None:
        self._configured.__exit__(None, None, None)
        close = getattr(self._device, "release", None)
        if close:
            close()
