"""Diagnose, health, and the audit export. Clients off the private range are refused."""

from __future__ import annotations

import asyncio
import hashlib
import logging
import re
import time
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response, StreamingResponse
from starlette.datastructures import UploadFile

from farm_ai.api.audit import AuditLog
from farm_ai.api.security import (
    RateLimiter,
    bearer_token,
    canonical_device_id,
    ensure_private_bind,
    is_jpeg_or_png,
    is_private_host,
    load_token,
    token_matches,
)
from farm_ai.config import LANGUAGES, Settings
from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.llm.factory import advise, llm_for_vision
from farm_ai.llm.fallback import fallback_advice
from farm_ai.vision.factory import create_vision
from farm_ai.vision.types import PreparedImage


_CROP = re.compile(r"[a-z0-9 _-]{1,40}")
_BODY_MARGIN = 64 * 1024
_LOG = logging.getLogger("farm_ai")


class JSON(JSONResponse):
    def render(self, content: Any) -> bytes:
        import json

        return json.dumps(content, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    ensure_private_bind(settings.host)
    token = load_token(settings.token_path)
    kb = KnowledgeBase(settings.kb_dir)
    vision = create_vision(settings, kb)
    llm = llm_for_vision(settings, vision.name)
    audit = AuditLog(settings.db_path)
    audit.write(
        action="settings",
        device_id="service",
        backend=vision.name,
        model_id=vision.model_id,
        model_version=vision.model_version,
    )

    app = FastAPI(title="Fam-AI", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = settings
    app.state.token = token
    app.state.kb = kb
    app.state.vision = vision
    app.state.llm = llm
    app.state.audit = audit
    app.state.limiter = RateLimiter(settings.diagnose_per_minute)

    @app.middleware("http")
    async def private_only(request: Request, call_next):
        host = request.client.host if request.client else ""
        if not is_private_host(host):
            request.app.state.audit.write(action="rejected")
            return _error("forbidden", 403)
        return await call_next(request)

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, exc: HTTPException):
        detail = exc.detail if isinstance(exc.detail, str) else "rejected"
        if detail not in {"unauthorized", "rejected", "rate_limited", "forbidden", "internal"}:
            detail = "rejected"
        return _error(detail, exc.status_code)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        del request, exc
        return _error("rejected", 400)

    @app.exception_handler(Exception)
    async def unhandled(request: Request, exc: Exception):
        del request
        _LOG.exception("request failed: %s", type(exc).__name__)
        return _error("internal", 500)

    @app.get("/v1/health")
    async def health(request: Request):
        return JSON(
            {
                "vision_backend": request.app.state.vision.name,
                "llm_backend": request.app.state.llm.name,
            },
            headers={"Cache-Control": "no-store"},
        )

    @app.get("/v1/language-pack/{code}")
    async def language_pack(code: str, request: Request):
        provided = bearer_token(request.headers.get("authorization"))
        if not token_matches(provided, request.app.state.token):
            request.app.state.audit.write(action="auth_failed")
            return _error("unauthorized", 401)
        path = request.app.state.kb.pack_path(code)
        if path is None:
            return _error("rejected", 404)
        payload = path.read_bytes()
        return Response(
            content=payload,
            media_type="application/json",
            headers={"Cache-Control": "no-store"},
        )

    @app.get("/v1/audit")
    async def audit_csv(request: Request):
        provided = bearer_token(request.headers.get("authorization"))
        if not token_matches(provided, request.app.state.token):
            request.app.state.audit.write(action="auth_failed")
            return _error("unauthorized", 401)
        log: AuditLog = request.app.state.audit
        return StreamingResponse(
            log.csv_lines(),
            media_type="text/csv; charset=utf-8",
            headers={
                "Cache-Control": "no-store",
                "Content-Disposition": 'attachment; filename="audit.csv"',
            },
        )

    @app.post("/v1/diagnose")
    async def diagnose(request: Request):
        return await _diagnose(request)

    return app


def _error(code: str, status: int) -> JSON:
    return JSON({"error": code}, status_code=status, headers={"Cache-Control": "no-store"})


def _field(form, key: str) -> str:
    value = form.get(key, "")
    if isinstance(value, UploadFile):
        return ""
    return str(value).strip()


async def _diagnose(request: Request):
    state = request.app.state
    settings: Settings = state.settings
    audit: AuditLog = state.audit
    kb: KnowledgeBase = state.kb

    def reject(status: int, code: str, action: str, **fields):
        audit.write(action=action, **fields)
        return _error(code, status)

    length = _content_length(request)
    if length is None or length > settings.max_upload_bytes + _BODY_MARGIN:
        return reject(400, "rejected", "rejected")
    content_type = request.headers.get("content-type", "")
    if "multipart/form-data" not in content_type.lower():
        return reject(400, "rejected", "rejected")
    try:
        form = await request.form()
    except Exception:
        _LOG.exception("could not read the form")
        return reject(400, "rejected", "rejected")

    device_id = canonical_device_id(_field(form, "device_id")) or ""
    language = _field(form, "language").lower()
    region_id = _field(form, "region_id")
    safe_language = language if language in LANGUAGES else ""
    safe_region = region_id if kb.region(region_id) is not None else ""

    provided = bearer_token(request.headers.get("authorization"))
    if not token_matches(provided, state.token):
        return reject(
            401,
            "unauthorized",
            "auth_failed",
            device_id=device_id,
            language=safe_language,
            region=safe_region,
        )
    if not device_id:
        return reject(400, "rejected", "rejected")
    if language not in LANGUAGES:
        return reject(400, "rejected", "rejected", device_id=device_id)
    if safe_region == "":
        return reject(400, "rejected", "rejected", device_id=device_id, language=language)
    crop = _field(form, "crop").lower()
    if crop and _CROP.fullmatch(crop) is None:
        return reject(
            400,
            "rejected",
            "rejected",
            device_id=device_id,
            language=language,
            region=region_id,
        )
    if not state.limiter.allow(device_id):
        return reject(
            429,
            "rate_limited",
            "rate_limited",
            device_id=device_id,
            language=language,
            region=region_id,
        )

    upload = form.get("image")
    if not isinstance(upload, UploadFile):
        return reject(
            400,
            "rejected",
            "rejected",
            device_id=device_id,
            language=language,
            region=region_id,
        )
    try:
        data = await upload.read(settings.max_upload_bytes + 1)
    finally:
        await upload.close()
    if len(data) > settings.max_upload_bytes:
        return reject(
            400,
            "rejected",
            "rejected",
            device_id=device_id,
            language=language,
            region=region_id,
        )
    digest = hashlib.sha256(data).hexdigest()
    if not is_jpeg_or_png(data):
        return reject(
            400,
            "rejected",
            "rejected",
            device_id=device_id,
            language=language,
            region=region_id,
            image_sha256=digest,
        )

    try:
        started = time.perf_counter()
        prepared = await asyncio.to_thread(PreparedImage.from_bytes, data)
        prediction = await asyncio.to_thread(state.vision.predict, prepared, crop or None)
        label = kb.label(prediction.label_id)
        region = kb.region(region_id)
        if region is None:
            return reject(
                400,
                "rejected",
                "rejected",
                device_id=device_id,
                language=language,
                region=region_id,
                image_sha256=digest,
            )
        treatment, region, stored_language = kb.localized(prediction.label_id, region, language)
        diagnosis = {
            "label_id": prediction.label_id,
            "display_name": label["display_name"],
            "crop": label["crop"],
            "kind": label["kind"],
            "confidence": prediction.confidence,
            "severity": prediction.severity,
            "lesion_ratio": prediction.lesion_ratio,
        }
        if state.vision.name == "hailo":
            advice = fallback_advice(treatment, region, language)
            source = "template"
            advice_language = stored_language
        else:
            advice, source, advice_language = await asyncio.to_thread(
                advise,
                state.llm,
                kb,
                language=language,
                region=region,
                treatment=treatment,
                diagnosis=diagnosis,
            )
        latency_ms = int((time.perf_counter() - started) * 1000)
    except ValueError:
        return reject(
            400,
            "rejected",
            "rejected",
            device_id=device_id,
            language=language,
            region=region_id,
            image_sha256=digest,
        )
    except Exception:
        _LOG.exception("diagnose failed")
        return _error("internal", 500)

    body = {
        "label_id": prediction.label_id,
        "display_name": label["display_name"],
        "crop": label["crop"],
        "kind": label["kind"],
        "confidence": prediction.confidence,
        "severity": prediction.severity,
        "lesion_ratio": prediction.lesion_ratio,
        "model_id": prediction.model_id,
        "model_version": prediction.model_version,
        "backend": prediction.backend,
        "latency_ms": latency_ms,
        "image_sha256": digest,
        "advice_source": source,
        "advice_language": advice_language,
        "advice": {
            "summary": advice["summary"],
            "immediate_actions": list(advice["immediate_actions"]),
            "treatment": list(advice["treatment"]),
            "prevention": list(advice["prevention"]),
            "when_to_seek_help": advice["when_to_seek_help"],
        },
    }
    audit.write(
        action="diagnose",
        device_id=device_id,
        label=prediction.label_id,
        severity=prediction.severity,
        backend=prediction.backend,
        model_id=prediction.model_id,
        model_version=prediction.model_version,
        language=language,
        region=region_id,
        latency_ms=latency_ms,
        image_sha256=digest,
        advice_source=source,
    )
    return JSON(body, headers={"Cache-Control": "no-store"})


def _content_length(request: Request) -> int | None:
    raw = request.headers.get("content-length")
    if raw is None:
        return None
    try:
        length = int(raw)
    except ValueError:
        return None
    if length < 0:
        return None
    return length
