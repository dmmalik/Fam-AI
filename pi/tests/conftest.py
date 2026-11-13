import io
import os
from pathlib import Path

import pytest
from PIL import Image

from farm_ai.config import Settings


KB = Path(__file__).resolve().parents[2] / "kb"
DEVICE_ID = "550e8400-e29b-41d4-a716-446655440000"
TOKEN = "test-token-value"


@pytest.fixture
def settings(tmp_path):
    token = tmp_path / "token"
    token.write_text(TOKEN + "\n", encoding="utf-8")
    os.chmod(token, 0o600)
    return Settings(
        vision_backend="mock",
        llm_backend="mock",
        hailo_enabled=False,
        classifier_pte=tmp_path / "missing.pte",
        classifier_hef=tmp_path / "missing.hef",
        llama_pte=tmp_path / "missing-llama.pte",
        kb_dir=KB,
        token_path=token,
        db_path=tmp_path / "audit.db",
        host="192.168.4.1",
        port=8080,
    )


def png_bytes(color=(30, 150, 40), size=(32, 32)) -> bytes:
    image = Image.new("RGB", size, color)
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def jpeg_bytes(color=(140, 90, 40)) -> bytes:
    image = Image.new("RGB", (24, 24), color)
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG")
    return buffer.getvalue()
