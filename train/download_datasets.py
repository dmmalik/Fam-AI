"""Download the public plant-disease sets used for EfficientNetV2 training.

PlantVillage is the unaugmented Mendeley zip so copied leaves are not in both
the train and validation split. Indian soybean and the multi-crop set are the
Mendeley dataset zips, which keep the class folders. PlantDoc is the
Hugging Face mirror geraldmc/plantdoc-full (CC BY 4.0).

Kaggle, IEEE Dataport, and Zenodo are not fetched here: Kaggle and IEEE need
an account, and the Zenodo API refused this machine.
"""

from __future__ import annotations

import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ZIPS = ROOT / "data" / "zips"
UA = "Mozilla/5.0 (compatible; FamAI-train/1.0)"

FILES = {
    "plantvillage_no_aug.zip": (
        "https://data.mendeley.com/public-files/datasets/tywbtsjrjv/files/"
        "d5652a28-c1d8-4b76-97f3-72fb80f94efc/file_downloaded"
    ),
    **{
        f"plantvillage-train-{index:05d}-of-00013.parquet": (
            "https://huggingface.co/datasets/dpdl-benchmark/plant_village/resolve/main/"
            f"data/train-{index:05d}-of-00013.parquet?download=true"
        )
        for index in range(13)
    },
    "soybean.zip": "https://data.mendeley.com/public-api/zip/bshkvgbzpt/download/1",
    "multicrop.zip": "https://data.mendeley.com/public-api/zip/6243z8r6t6/download/1",
    "plantdoc-train-00000-of-00002.parquet": (
        "https://huggingface.co/datasets/geraldmc/plantdoc-full/resolve/main/"
        "data/train-00000-of-00002.parquet?download=true"
    ),
    "plantdoc-train-00001-of-00002.parquet": (
        "https://huggingface.co/datasets/geraldmc/plantdoc-full/resolve/main/"
        "data/train-00001-of-00002.parquet?download=true"
    ),
}


def fetch(name: str, url: str) -> None:
    dest = ZIPS / name
    part = dest.with_suffix(dest.suffix + ".partial")
    done = 0
    if part.exists():
        done = part.stat().st_size
    elif dest.exists() and dest.stat().st_size > 0:
        print(f"have {name} ({dest.stat().st_size} bytes)", flush=True)
        return
    headers = {"User-Agent": UA}
    if done:
        headers["Range"] = f"bytes={done}-"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=120) as response:
        code = getattr(response, "status", 200)
        if code == 416:
            part.rename(dest)
            print(f"complete {name}", flush=True)
            return
        if code == 200 and done:
            done = 0
        mode = "ab" if code == 206 else "wb"
        total = response.headers.get("Content-Range")
        if total and "/" in total:
            total_bytes = int(total.rsplit("/", 1)[-1])
        else:
            length = response.headers.get("Content-Length")
            total_bytes = done + int(length) if length else 0
        print(f"start {name} at {done} of {total_bytes}", flush=True)
        last = time.time()
        with part.open(mode) as handle:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                handle.write(chunk)
                done += len(chunk)
                now = time.time()
                if now - last >= 15:
                    if total_bytes:
                        print(
                            f"{name} {done / total_bytes:.1%} {done}/{total_bytes}",
                            flush=True,
                        )
                    else:
                        print(f"{name} {done}", flush=True)
                    last = now
    if dest.exists():
        dest.unlink()
    part.rename(dest)
    print(f"saved {name} {dest.stat().st_size}", flush=True)


def main() -> None:
    ZIPS.mkdir(parents=True, exist_ok=True)
    only = sys.argv[1:]
    for name, url in FILES.items():
        if only and name not in only:
            continue
        fetch(name, url)


if __name__ == "__main__":
    main()
