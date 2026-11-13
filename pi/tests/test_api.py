import csv
import hashlib
import io
import os
import sqlite3

import pytest
from fastapi.testclient import TestClient

from farm_ai.api.app import create_app
from farm_ai.config import Settings
from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.llm.fallback import fallback_advice
from farm_ai.vision.mock import MockVision
from farm_ai.vision.types import VisionPrediction

from conftest import DEVICE_ID, KB, TOKEN, png_bytes


def _client(app, host="192.168.4.20"):
    return TestClient(app, client=(host, 50000))


def _diagnose(client, image, token=TOKEN, **fields):
    data = {
        "language": "en",
        "region_id": "indo_gangetic",
        "device_id": DEVICE_ID,
    }
    data.update(fields)
    return client.post(
        "/v1/diagnose",
        data=data,
        files={"image": ("leaf.png", image, "image/png")},
        headers={"Authorization": f"Bearer {token}"},
    )


def _rows(client):
    response = client.get("/v1/audit", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200
    parsed = list(csv.DictReader(io.StringIO(response.text)))
    return response.text, parsed


def test_health_names_backends_without_a_photo(settings):
    with _client(create_app(settings)) as client:
        response = client.get("/v1/health")
    assert response.status_code == 200
    assert response.json() == {"vision_backend": "mock", "llm_backend": "mock"}


def test_warm_is_not_repeated_per_request(monkeypatch, settings):
    calls = {"n": 0}
    original = MockVision.warm

    def wrapped(self):
        calls["n"] += 1
        return original(self)

    monkeypatch.setattr(MockVision, "warm", wrapped)
    with _client(create_app(settings)) as client:
        assert client.get("/v1/health").status_code == 200
        assert _diagnose(client, png_bytes()).status_code == 200
    assert calls["n"] == 1


def test_mock_diagnose_writes_one_audit_row(settings):
    image = png_bytes()
    digest = hashlib.sha256(image).hexdigest()
    kb = KnowledgeBase(KB)
    with _client(create_app(settings)) as client:
        response = _diagnose(client, image)
        assert response.status_code == 200
        body = response.json()
        _, rows = _rows(client)
    assert set(body) == {
        "label_id",
        "display_name",
        "crop",
        "kind",
        "confidence",
        "severity",
        "lesion_ratio",
        "model_id",
        "model_version",
        "backend",
        "latency_ms",
        "image_sha256",
        "advice_source",
        "advice_language",
        "advice",
    }
    assert set(body["advice"]) == {
        "summary",
        "immediate_actions",
        "treatment",
        "prevention",
        "when_to_seek_help",
    }
    assert body["backend"] == "mock"
    assert body["model_id"] == "mock"
    assert body["model_version"] == "none"
    assert body["advice_source"] == "template"
    assert body["advice_language"] == "en"
    assert body["image_sha256"] == digest
    assert body["severity"] in {"low", "medium", "high"}
    assert isinstance(body["latency_ms"], int) and body["latency_ms"] >= 0
    expected = fallback_advice(
        kb.treatment(body["label_id"]),
        kb.region("indo_gangetic"),
        "en",
    )
    assert body["advice"] == expected
    diagnose_rows = [row for row in rows if row["action"] == "diagnose"]
    assert len(diagnose_rows) == 1
    row = diagnose_rows[0]
    assert row["device_id"] == DEVICE_ID
    assert row["label"] == body["label_id"]
    assert row["severity"] == body["severity"]
    assert row["backend"] == "mock"
    assert row["model_id"] == "mock"
    assert row["model_version"] == "none"
    assert row["language"] == "en"
    assert row["region"] == "indo_gangetic"
    assert row["latency_ms"] == str(body["latency_ms"])
    assert row["image_sha256"] == digest
    assert row["advice_source"] == "template"
    assert row["time"].endswith("Z")


def test_bad_token_and_rejected_upload_are_audited(settings):
    with _client(create_app(settings)) as client:
        missing = client.post(
            "/v1/diagnose",
            data={"language": "en", "region_id": "indo_gangetic", "device_id": DEVICE_ID},
            files={"image": ("leaf.png", png_bytes(), "image/png")},
        )
        wrong = _diagnose(client, png_bytes(), token="not-the-token")
        rejected = _diagnose(client, b"GIF89a this is not a leaf")
        _, rows = _rows(client)
    assert missing.status_code == 401
    assert missing.json() == {"error": "unauthorized"}
    assert "traceback" not in missing.text.lower()
    assert wrong.status_code == 401
    assert wrong.json() == {"error": "unauthorized"}
    assert rejected.status_code == 400
    assert rejected.json() == {"error": "rejected"}
    assert "traceback" not in rejected.text.lower()
    actions = [row["action"] for row in rows]
    assert actions.count("auth_failed") == 2
    assert any(row["action"] == "rejected" and row["image_sha256"] for row in rows)
    assert not any(row["action"] == "diagnose" for row in rows)


def test_public_client_is_refused(settings):
    app = create_app(settings)
    calls = {"n": 0}
    original = app.state.vision.predict

    def wrapped(image, crop):
        calls["n"] += 1
        return original(image, crop)

    app.state.vision.predict = wrapped
    with _client(app, host="8.8.8.8") as client:
        response = client.get("/v1/health")
    assert response.status_code == 403
    assert response.json() == {"error": "forbidden"}
    assert calls["n"] == 0
    with _client(app) as client:
        _, rows = _rows(client)
    assert any(row["action"] == "rejected" and row["device_id"] == "" for row in rows)


def test_audit_csv_header_and_append_only(settings):
    app = create_app(settings)
    with _client(app) as client:
        text, _rows_parsed = _rows(client)
    header = next(csv.reader(io.StringIO(text)))
    assert header == [
        "time",
        "device_id",
        "action",
        "label",
        "severity",
        "backend",
        "model_id",
        "model_version",
        "language",
        "region",
        "latency_ms",
        "image_sha256",
        "advice_source",
    ]
    mode = settings.db_path.stat().st_mode & 0o777
    assert mode == 0o600
    connection = sqlite3.connect(settings.db_path)
    with pytest.raises(sqlite3.IntegrityError):
        connection.execute("UPDATE audit SET action = 'diagnose'")
    with pytest.raises(sqlite3.IntegrityError):
        connection.execute("DELETE FROM audit")
    methods = set()
    for route in app.routes:
        methods.update(getattr(route, "methods", set()))
    assert "DELETE" not in methods
    assert "PUT" not in methods
    assert "PATCH" not in methods


def test_rate_limit(settings):
    image = png_bytes()
    with _client(create_app(settings)) as client:
        statuses = [_diagnose(client, image).status_code for _ in range(11)]
        _, rows = _rows(client)
    assert statuses[:10] == [200] * 10
    assert statuses[10] == 429
    assert sum(row["action"] == "rate_limited" for row in rows) == 1


def test_hailo_request_does_not_call_llama(settings):
    app = create_app(settings)
    label = app.state.kb.labels[0]
    calls = {"llama": 0}

    class HailoStandIn:
        name = "hailo"
        model_id = "efficientnetv2_s_int8_384"
        model_version = "abc123"

        def predict(self, image, crop):
            return VisionPrediction(
                label_id=label["id"],
                confidence=0.91,
                severity="low",
                lesion_ratio=0.0,
                model_id=self.model_id,
                model_version=self.model_version,
                backend=self.name,
            )

    class LlamaStandIn:
        name = "executorch"

        def generate(self, prompt):
            calls["llama"] += 1
            return None

    app.state.vision = HailoStandIn()
    app.state.llm = LlamaStandIn()
    with _client(app) as client:
        response = _diagnose(client, png_bytes(), language="hi", region_id="peninsular_india")
    assert response.status_code == 200
    body = response.json()
    assert calls["llama"] == 0
    assert body["backend"] == "hailo"
    assert body["advice_source"] == "template"
    assert body["advice"]["summary"] == app.state.kb.treatment(label["id"])["summary"]["hi"]


def test_unknown_language_is_rejected_and_tamil_uses_the_pack(settings):
    with _client(create_app(settings)) as client:
        rejected = _diagnose(client, png_bytes(), language="zz")
        accepted = _diagnose(client, png_bytes(), language="ta")
        missing = client.get("/v1/language-pack/en", headers={"Authorization": f"Bearer {TOKEN}"})
        hidden = client.get("/v1/language-pack/ta")
        packed = client.get(
            "/v1/language-pack/ta",
            headers={"Authorization": f"Bearer {TOKEN}"},
        )
    assert rejected.status_code == 400
    assert accepted.status_code == 200
    body = accepted.json()
    assert body["advice_source"] == "template"
    assert "summary" in body["advice"]
    assert missing.status_code == 404
    assert hidden.status_code == 401
    if packed.status_code == 200:
        pack = packed.json()
        assert pack["code"] == "ta"
        assert pack["version"] == 1
        assert body["advice_language"] == "ta"
        assert body["advice"]["summary"] == pack["treatments"][body["label_id"]]["summary"]
    else:
        assert packed.status_code == 404
        assert body["advice_language"] == "en"


def test_token_file_must_be_mode_0600(tmp_path):
    token = tmp_path / "token"
    token.write_text("secret\n", encoding="utf-8")
    os.chmod(token, 0o644)
    settings = Settings(
        vision_backend="mock",
        llm_backend="mock",
        token_path=token,
        db_path=tmp_path / "audit.db",
        kb_dir=KB,
        host="192.168.4.1",
    )
    with pytest.raises(RuntimeError, match="0600"):
        create_app(settings)
