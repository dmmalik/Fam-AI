"""Download the extra fine-tune sets with a progress line and ETA.

Each line is one snapshot: percent, bytes, speed, and time left.
A one-line status file is overwritten at train/data/extra/STATUS.
"""

from __future__ import annotations

import json
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "data" / "extra"
UA = "Mozilla/5.0 (compatible; FamAI-train/1.0)"
WORKERS = 6

PARQUETS = {
    "rice-india": [
        (
            "train-00000-of-00001.parquet",
            "https://huggingface.co/datasets/Project-AgML/rice_leaf_disease_classification_india/"
            "resolve/main/data/train-00000-of-00001.parquet?download=true",
        )
    ],
    "paddy-doctor": [
        (
            "train-00000-of-00002.parquet",
            "https://huggingface.co/datasets/anthony2261/paddy-disease-classification/resolve/main/"
            "data/train-00000-of-00002-a929229f3aaf7166.parquet?download=true",
        ),
        (
            "train-00001-of-00002.parquet",
            "https://huggingface.co/datasets/anthony2261/paddy-disease-classification/resolve/main/"
            "data/train-00001-of-00002-4c2a20b9469e90df.parquet?download=true",
        ),
    ],
    "insect-pests": [
        (
            name,
            "https://huggingface.co/datasets/EnmmmmOvO/insect-pest-dataset/resolve/main/"
            f"{name}?download=true",
        )
        for name in (
            "data/train-00000-of-00004.parquet",
            "data/train-00001-of-00004.parquet",
            "data/train-00002-of-00004.parquet",
            "data/train-00003-of-00004.parquet",
            "data/validation-00000-of-00001.parquet",
            "data/test-00000-of-00002.parquet",
            "data/test-00001-of-00002.parquet",
        )
    ],
}

TREES = {
    "ccmt": "yourspace/CCMTDataset",
    "cassava": "Engineer101/cassava-leaf-disease-classification",
}


def hf_files(repo: str) -> list[tuple[str, str]]:
    url = f"https://huggingface.co/api/datasets/{repo}"
    request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = json.load(response)
    jobs = []
    for item in data.get("siblings") or []:
        name = item["rfilename"]
        if name == ".gitattributes":
            continue
        file_url = (
            f"https://huggingface.co/datasets/{repo}/resolve/main/"
            f"{urllib.request.pathname2url(name)}?download=true"
        )
        jobs.append((name, file_url))
    return jobs


def content_length(url: str) -> int:
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=60) as response:
        return int(response.headers.get("Content-Length") or 0)


def fmt_eta(seconds: float) -> str:
    seconds = max(0, int(seconds))
    hours, rem = divmod(seconds, 3600)
    minutes, secs = divmod(rem, 60)
    if hours:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    return f"{minutes}:{secs:02d}"


class Bar:
    def __init__(self, name: str, total: int, unit: str):
        self.name = name
        self.total = max(total, 1)
        self.unit = unit
        self.done = 0
        self.start = time.time()
        self.lock = threading.Lock()
        self.last = 0.0

    def add(self, count: int) -> None:
        with self.lock:
            self.done += count
            now = time.time()
            if now - self.last >= 1 or self.done >= self.total:
                self.last = now
                self.show()

    def show(self) -> None:
        elapsed = max(time.time() - self.start, 0.001)
        speed = self.done / elapsed
        remain = (self.total - self.done) / speed if speed else 0
        frac = min(self.done / self.total, 1)
        width = 28
        filled = int(width * frac)
        bar = "#" * filled + "." * (width - filled)
        if self.unit == "MB":
            amount = f"{self.done / 1e6:7.1f}/{self.total / 1e6:7.1f} MB  {speed / 1e6:4.1f} MB/s"
        else:
            amount = f"{self.done:5}/{self.total:<5} files  {speed:4.1f} files/s"
        line = f"{self.name:<14} [{bar}] {frac:6.1%}  {amount}  ETA {fmt_eta(remain)}"
        print(line, flush=True)
        (OUT / "STATUS").write_text(line + "\n")


def fetch_one(dest: Path, url: str, bar: Bar | None) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        if bar is not None:
            bar.add(dest.stat().st_size)
        return
    part = dest.with_suffix(dest.suffix + ".partial")
    have = part.stat().st_size if part.exists() else 0
    headers = {"User-Agent": UA}
    if have:
        headers["Range"] = f"bytes={have}-"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=180) as response:
        code = getattr(response, "status", 200)
        mode = "ab" if code == 206 else "wb"
        if code == 200:
            have = 0
        with part.open(mode) as handle:
            while True:
                chunk = response.read(1024 * 256)
                if not chunk:
                    break
                handle.write(chunk)
                have += len(chunk)
                if bar is not None:
                    bar.add(len(chunk))
    if dest.exists():
        dest.unlink()
    part.rename(dest)


def run_bytes(name: str, jobs: list[tuple[str, str]]) -> None:
    folder = OUT / name
    sized = []
    known = 0
    for rel, url in jobs:
        dest = folder / rel
        if dest.exists() and dest.stat().st_size > 0:
            size = dest.stat().st_size
        else:
            size = content_length(url)
        sized.append((dest, url, size))
        known += size
    print(f"start {name}: {len(jobs)} files, {known / 1e6:.1f} MB", flush=True)
    bar = Bar(name, known, "MB")
    errors: list[str] = []

    def work(item):
        dest, url, _size = item
        try:
            fetch_one(dest, url, bar)
        except Exception as error:
            errors.append(f"{dest.name}: {error}")

    from concurrent.futures import ThreadPoolExecutor

    with ThreadPoolExecutor(WORKERS) as pool:
        list(pool.map(work, sized))
    finish(name, folder, bar, errors)


def run_files(name: str, jobs: list[tuple[str, str]]) -> None:
    folder = OUT / name
    print(f"start {name}: {len(jobs)} files", flush=True)
    bar = Bar(name, len(jobs), "files")
    errors: list[str] = []

    def work(item):
        rel, url = item
        dest = folder / rel
        try:
            if dest.exists() and dest.stat().st_size > 0:
                bar.add(1)
                return
            fetch_one(dest, url, bar=None)
            bar.add(1)
        except Exception as error:
            errors.append(f"{rel}: {error}")

    from concurrent.futures import ThreadPoolExecutor

    with ThreadPoolExecutor(WORKERS) as pool:
        list(pool.map(work, jobs))
    finish(name, folder, bar, errors)


def finish(name: str, folder: Path, bar: Bar, errors: list[str]) -> None:
    bar.show()
    if errors:
        print(f"{name} errors {len(errors)}", flush=True)
        (folder / "errors.txt").write_text("\n".join(errors[:200]) + "\n")
    else:
        print(f"saved {name}", flush=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, jobs in PARQUETS.items():
        run_bytes(name, jobs)
    for name, repo in TREES.items():
        print(f"listing {repo}", flush=True)
        run_files(name, hf_files(repo))
    print("all extra datasets saved", flush=True)


if __name__ == "__main__":
    main()
