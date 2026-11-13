"""Token check, private-address check, and the per-device diagnose cap."""

from __future__ import annotations

import hashlib
import hmac
import ipaddress
import os
import stat
import threading
import time
import uuid
from collections import deque
from pathlib import Path


_PRIVATE_NETS = (
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("::1/128"),
    ipaddress.ip_network("fc00::/7"),
    ipaddress.ip_network("fe80::/10"),
)


def is_private_host(host: str) -> bool:
    if not host:
        return False
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return False
    mapped = getattr(address, "ipv4_mapped", None)
    if mapped is not None:
        address = mapped
    return any(address in network for network in _PRIVATE_NETS)


def ensure_private_bind(host: str) -> None:
    if host in {"0.0.0.0", "::", ""}:
        raise RuntimeError("refusing to bind all interfaces")
    if not is_private_host(host):
        raise RuntimeError("bind address is not a private hotspot address")


def load_token(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"token file missing: {path}")
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode != 0o600:
        raise RuntimeError("token file must be mode 0600")
    lines = path.read_text(encoding="utf-8").splitlines()
    token = lines[0].strip() if lines else ""
    if not token:
        raise RuntimeError("token file is empty")
    return token


def bearer_token(header: str | None) -> str | None:
    if not header:
        return None
    scheme, _, value = header.partition(" ")
    if scheme.lower() != "bearer" or not value.strip():
        return None
    return value.strip()


def token_matches(provided: str | None, expected: str) -> bool:
    left = hashlib.sha256((provided or "").encode("utf-8")).digest()
    right = hashlib.sha256(expected.encode("utf-8")).digest()
    return hmac.compare_digest(left, right)


def canonical_device_id(value: str) -> str | None:
    try:
        return str(uuid.UUID(value.strip()))
    except (ValueError, AttributeError):
        return None


def is_jpeg_or_png(data: bytes) -> bool:
    return data.startswith(b"\xff\xd8\xff") or data.startswith(b"\x89PNG\r\n\x1a\n")


def enforce_mode_0600(path: Path) -> None:
    os.chmod(path, 0o600)
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode != 0o600:
        raise RuntimeError(f"could not set mode 0600 on {path}")


class RateLimiter:
    """About ten diagnose calls a minute for one device id."""

    def __init__(self, limit: int = 10, window_s: float = 60.0) -> None:
        self.limit = limit
        self.window_s = window_s
        self._hits: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def allow(self, device_id: str) -> bool:
        now = time.monotonic()
        with self._lock:
            hits = self._hits.setdefault(device_id, deque())
            while hits and now - hits[0] > self.window_s:
                hits.popleft()
            if len(hits) >= self.limit:
                return False
            hits.append(now)
            return True
