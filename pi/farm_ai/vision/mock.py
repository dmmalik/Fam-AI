"""Deterministic stand-in used when weights are not on the device."""

from __future__ import annotations

import hashlib

from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.vision.severity import severity_for
from farm_ai.vision.types import PreparedImage, VisionPrediction


class MockVision:
    name = "mock"
    model_id = "mock"
    model_version = "none"

    def __init__(self, kb: KnowledgeBase) -> None:
        self._labels = kb.labels

    def warm(self) -> None:
        self.predict(PreparedImage.blank(), None)

    def predict(self, image: PreparedImage, crop: str | None) -> VisionPrediction:
        digest = hashlib.sha256(image.rgb_uint8.tobytes()).digest()
        hint = (crop or "").lower()
        if hint:
            pool = [item for item in self._labels if item["crop"] in hint or hint in item["crop"]]
        else:
            pool = list(self._labels)
        if not pool:
            pool = list(self._labels)
        label = pool[digest[0] % len(pool)]
        if label["id"].endswith("_healthy"):
            severity = "low"
        else:
            confidence_preview = 0.55 + (digest[1] / 255) * 0.4
            severity = severity_for(label["kind"], image.lesion_ratio, confidence_preview)
        confidence = 0.55 + (digest[1] / 255) * 0.4
        return VisionPrediction(
            label_id=label["id"],
            confidence=round(confidence, 4),
            severity=severity,
            lesion_ratio=round(image.lesion_ratio, 4),
            model_id=self.model_id,
            model_version=self.model_version,
            backend=self.name,
        )
