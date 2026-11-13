import hashlib
from pathlib import Path

import numpy as np
import pytest

from farm_ai.config import Settings
from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.llm.factory import ExecuTorchLlama, llm_for_vision
from farm_ai.vision.factory import BackendError, create_vision, select_backend_name
from farm_ai.vision.types import PreparedImage

from conftest import KB


def test_stub_modules_import():
    import farm_ai.config  # noqa: F401
    import farm_ai.kb.retrieve  # noqa: F401
    import farm_ai.llm.executorch_llama  # noqa: F401
    import farm_ai.llm.factory  # noqa: F401
    import farm_ai.llm.fallback  # noqa: F401
    import farm_ai.llm.parser  # noqa: F401
    import farm_ai.llm.prompt  # noqa: F401
    import farm_ai.vision.cpu_executorch  # noqa: F401
    import farm_ai.vision.factory  # noqa: F401
    import farm_ai.vision.hailo_backend  # noqa: F401
    import farm_ai.vision.mock  # noqa: F401
    import farm_ai.vision.severity  # noqa: F401
    import farm_ai.vision.types  # noqa: F401


def test_backend_selection(monkeypatch, tmp_path):
    pte = tmp_path / "model.pte"
    pte.write_bytes(b"weights")
    missing = tmp_path / "missing.pte"

    monkeypatch.setattr("farm_ai.vision.factory.hailo_runtime_available", lambda: False)
    assert select_backend_name(Settings(vision_backend="mock", classifier_pte=pte)) == "mock"
    assert select_backend_name(Settings(vision_backend="cpu", classifier_pte=missing)) == "cpu"
    assert select_backend_name(Settings(vision_backend="hailo")) == "hailo"
    assert (
        select_backend_name(
            Settings(vision_backend=None, hailo_enabled=False, classifier_pte=pte)
        )
        == "cpu"
    )
    assert (
        select_backend_name(
            Settings(vision_backend=None, hailo_enabled=True, classifier_pte=pte)
        )
        == "cpu"
    )
    assert (
        select_backend_name(
            Settings(vision_backend=None, hailo_enabled=True, classifier_pte=missing)
        )
        == "mock"
    )
    monkeypatch.setattr("farm_ai.vision.factory.hailo_runtime_available", lambda: True)
    assert (
        select_backend_name(
            Settings(vision_backend=None, hailo_enabled=True, classifier_pte=missing)
        )
        == "hailo"
    )
    with pytest.raises(BackendError):
        select_backend_name(Settings(vision_backend="gpu"))


def test_unrequested_hailo_is_not_probed(monkeypatch, tmp_path):
    def explode():
        raise AssertionError("HailoRT was imported")

    monkeypatch.setattr("farm_ai.vision.factory.hailo_runtime_available", explode)
    settings = Settings(
        vision_backend=None,
        hailo_enabled=False,
        classifier_pte=tmp_path / "missing.pte",
    )
    assert select_backend_name(settings) == "mock"


def test_forced_hailo_without_runtime(monkeypatch, tmp_path):
    monkeypatch.setattr("farm_ai.vision.factory.hailo_runtime_available", lambda: False)
    kb = KnowledgeBase(KB)
    settings = Settings(
        vision_backend="hailo",
        classifier_hef=tmp_path / "missing.hef",
        classifier_pte=tmp_path / "missing.pte",
    )
    with pytest.raises(BackendError, match="HailoRT"):
        create_vision(settings, kb)


def test_cpu_does_not_read_hef_and_hashes_once(monkeypatch, tmp_path):
    kb = KnowledgeBase(KB)
    payload = b"pte-bytes-for-hash"
    pte = tmp_path / "model.pte"
    hef = tmp_path / "model.hef"
    pte.write_bytes(payload)
    hef.write_bytes(b"do-not-read")
    reads = {"n": 0}
    original = Path.read_bytes

    def counting(self):
        if self == pte:
            reads["n"] += 1
        return original(self)

    monkeypatch.setattr(Path, "read_bytes", counting)
    monkeypatch.setattr("farm_ai.vision.cpu_executorch._load_method", lambda path: object())
    monkeypatch.setattr(
        "farm_ai.vision.cpu_executorch._execute",
        lambda method, nchw: np.zeros(len(kb.labels), dtype=np.float32),
    )
    backend = create_vision(
        Settings(vision_backend="cpu", classifier_pte=pte, classifier_hef=hef),
        kb,
    )
    assert backend.model_version == hashlib.sha256(payload).hexdigest()[:16]
    assert reads["n"] == 1
    backend.predict(PreparedImage.blank(), None)
    assert reads["n"] == 1
    assert hef.read_bytes() == b"do-not-read"


def test_hailo_does_not_construct_llama(monkeypatch, tmp_path):
    def explode(self, model_path, max_new_tokens=256):
        raise AssertionError("Llama was loaded on the Hailo path")

    monkeypatch.setattr(ExecuTorchLlama, "__init__", explode)
    pte = tmp_path / "llama.pte"
    pte.write_bytes(b"llama")
    settings = Settings(llm_backend="executorch", llama_pte=pte)
    advisor = llm_for_vision(settings, "hailo")
    assert advisor.name == "template"
    assert advisor.generate("prompt") is None
