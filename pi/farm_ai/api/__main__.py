"""Start the hotspot API. The token file and the database stay outside the repo."""

from __future__ import annotations

import sys

import uvicorn

from farm_ai.api.app import create_app
from farm_ai.config import Settings


def main() -> None:
    try:
        settings = Settings.from_env()
        app = create_app(settings)
    except (OSError, RuntimeError, ValueError) as exc:
        print(f"fam-ai: {exc}", file=sys.stderr)
        raise SystemExit(1) from None
    uvicorn.run(app, host=settings.host, port=settings.port, log_level="info")


if __name__ == "__main__":
    main()
