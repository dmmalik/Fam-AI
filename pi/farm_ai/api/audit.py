"""Append-only audit log. Image bytes are not stored."""

from __future__ import annotations

import csv
import io
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path

from farm_ai.api.security import enforce_mode_0600


AUDIT_COLUMNS = (
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
)

ACTIONS = frozenset({"diagnose", "rejected", "auth_failed", "rate_limited", "settings"})

_INSERT = """
INSERT INTO audit (
    time, device_id, action, label, severity, backend, model_id,
    model_version, language, region, latency_ms, image_sha256, advice_source
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
"""

_SELECT = """
SELECT time, device_id, action, label, severity, backend, model_id,
       model_version, language, region, latency_ms, image_sha256, advice_source
FROM audit
ORDER BY id
"""


class AuditLog:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(
            self.path,
            timeout=5.0,
            check_same_thread=False,
            isolation_level=None,
        )
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA synchronous=NORMAL")
        self._conn.execute(
            """
            CREATE TABLE IF NOT EXISTS audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time TEXT NOT NULL,
                device_id TEXT NOT NULL DEFAULT '',
                action TEXT NOT NULL CHECK (
                    action IN ('diagnose', 'rejected', 'auth_failed', 'rate_limited', 'settings')
                ),
                label TEXT NOT NULL DEFAULT '',
                severity TEXT NOT NULL DEFAULT '',
                backend TEXT NOT NULL DEFAULT '',
                model_id TEXT NOT NULL DEFAULT '',
                model_version TEXT NOT NULL DEFAULT '',
                language TEXT NOT NULL DEFAULT '',
                region TEXT NOT NULL DEFAULT '',
                latency_ms INTEGER NOT NULL DEFAULT 0,
                image_sha256 TEXT NOT NULL DEFAULT '',
                advice_source TEXT NOT NULL DEFAULT ''
            )
            """
        )
        self._conn.execute(
            """
            CREATE TRIGGER IF NOT EXISTS audit_no_update
            BEFORE UPDATE ON audit
            BEGIN
                SELECT RAISE(ABORT, 'append-only');
            END
            """
        )
        self._conn.execute(
            """
            CREATE TRIGGER IF NOT EXISTS audit_no_delete
            BEFORE DELETE ON audit
            BEGIN
                SELECT RAISE(ABORT, 'append-only');
            END
            """
        )
        self._chmod()

    def write(
        self,
        *,
        action: str,
        device_id: str = "",
        label: str = "",
        severity: str = "",
        backend: str = "",
        model_id: str = "",
        model_version: str = "",
        language: str = "",
        region: str = "",
        latency_ms: int = 0,
        image_sha256: str = "",
        advice_source: str = "",
    ) -> None:
        if action not in ACTIONS:
            raise ValueError(f"unknown audit action: {action}")
        row = (
            datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            _text(device_id),
            action,
            _text(label),
            _text(severity),
            _text(backend),
            _text(model_id),
            _text(model_version),
            _text(language),
            _text(region),
            int(latency_ms),
            _text(image_sha256),
            _text(advice_source),
        )
        with self._lock:
            self._conn.execute(_INSERT, row)
            self._chmod()

    def rows(self) -> list[tuple]:
        with self._lock:
            cursor = self._conn.execute(_SELECT)
            return list(cursor.fetchall())

    def csv_lines(self):
        yield _csv_line(AUDIT_COLUMNS)
        for row in self.rows():
            yield _csv_line(row)

    def _chmod(self) -> None:
        enforce_mode_0600(self.path)
        for suffix in ("-wal", "-shm"):
            sidecar = Path(str(self.path) + suffix)
            if sidecar.exists():
                enforce_mode_0600(sidecar)


def _text(value: object) -> str:
    if value is None:
        return ""
    return str(value)


def _csv_line(values) -> str:
    buffer = io.StringIO()
    csv.writer(buffer, lineterminator="\n").writerow(values)
    return buffer.getvalue()
