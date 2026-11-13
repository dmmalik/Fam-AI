"""Download one Mendeley zip with several range requests.

A single connection to the Mendeley cache stays near 50 KB/s. Several ranges
at once are still slow, but they finish the soybean and multi-crop zips in
hours instead of a day. The public zip URL is resolved again if a range fails.
"""

from __future__ import annotations

import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

UA = "Mozilla/5.0 (compatible; FamAI-train/1.0)"
ROOT = Path(__file__).resolve().parent
ZIPS = ROOT / "data" / "zips"
CHUNK = 8 * 1024 * 1024


def resolve(url: str) -> tuple[str, int]:
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=120) as response:
        size = int(response.headers["Content-Length"])
        return response.geturl(), size


def read_range(url: str, start: int, end: int) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": UA, "Range": f"bytes={start}-{end}"},
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read()


def fetch(name: str, page: str, workers: int) -> None:
    dest = ZIPS / name
    if dest.exists() and dest.stat().st_size > 1_000_000:
        print(f"have {name}", flush=True)
        return
    final, size = resolve(page)
    part = dest.with_suffix(dest.suffix + ".partial")
    done = part.stat().st_size if part.exists() else 0
    if done > size:
        done = 0
    print(f"start {name} at {done} of {size} with {workers} connections", flush=True)
    ranges = []
    cursor = done
    while cursor < size:
        end = min(cursor + CHUNK, size) - 1
        ranges.append((cursor, end))
        cursor = end + 1
    pending = list(ranges)
    with part.open("ab" if done else "wb") as handle:
        while pending:
            batch = pending[:workers]
            pending = pending[workers:]
            try:
                with ThreadPoolExecutor(workers) as pool:
                    pieces = list(pool.map(lambda item: read_range(final, item[0], item[1]), batch))
            except Exception as error:
                print(f"refresh url after {error}", flush=True)
                final, size = resolve(page)
                pending = batch + pending
                time.sleep(2)
                continue
            for piece in pieces:
                handle.write(piece)
            handle.flush()
            print(f"{name} {handle.tell()}/{size}", flush=True)
    part.rename(dest)
    print(f"saved {name} {dest.stat().st_size}", flush=True)


def main() -> None:
    name = sys.argv[1]
    page = sys.argv[2]
    workers = int(sys.argv[3]) if len(sys.argv) > 3 else 8
    ZIPS.mkdir(parents=True, exist_ok=True)
    fetch(name, page, workers)


if __name__ == "__main__":
    main()
