"""Choose the vision backend once and keep it hot."""

from __future__ import annotations

from farm_ai.config import Settings
from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.vision.cpu_executorch import CpuExecuTorchVision
from farm_ai.vision.hailo_backend import HailoVision, hailo_runtime_available
from farm_ai.vision.mock import MockVision


class BackendError(RuntimeError):
    pass


def select_backend_name(settings: Settings) -> str:
    forced = settings.vision_backend
    if forced:
        if forced not in {"mock", "cpu", "hailo"}:
            raise BackendError(f"unknown vision backend: {forced}")
        return forced
    if settings.hailo_enabled and hailo_runtime_available():
        return "hailo"
    if settings.classifier_pte.is_file():
        return "cpu"
    return "mock"


def create_vision(settings: Settings, kb: KnowledgeBase):
    name = select_backend_name(settings)
    if name == "mock":
        backend = MockVision(kb)
    elif name == "cpu":
        try:
            backend = CpuExecuTorchVision(settings.classifier_pte, kb)
        except (FileNotFoundError, RuntimeError) as exc:
            raise BackendError(str(exc)) from exc
    else:
        if not hailo_runtime_available():
            raise BackendError("Hailo was requested but HailoRT is not installed")
        try:
            backend = HailoVision(settings.classifier_hef, kb)
        except (FileNotFoundError, RuntimeError) as exc:
            raise BackendError(str(exc)) from exc
    backend.warm()
    return backend
