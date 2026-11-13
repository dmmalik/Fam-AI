"""Turn the downloaded archives into one image manifest.

Overlapping diseases share one class id with kb/labels.json so a later
EfficientNet export can line up with the Pi label order. Crops that are not
in that list keep their own ids. Multi-crop names stay prefixed with field_,
because labels such as Rust and Black-Rot are not the maize or apple classes.
"""

from __future__ import annotations

import csv
import json
import re
import zipfile
from collections import defaultdict
from pathlib import Path

import pyarrow.parquet as pq

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
ZIPS = DATA / "zips"
EXTRACTED = DATA / "extracted"
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG", ".webp"}

PV = {
    "Apple___Apple_scab": "apple_scab",
    "Apple___Black_rot": "apple_black_rot",
    "Apple___Cedar_apple_rust": "apple_cedar_rust",
    "Apple___healthy": "apple_healthy",
    "Blueberry___healthy": "blueberry_healthy",
    "Cherry_(including_sour)___Powdery_mildew": "cherry_powdery_mildew",
    "Cherry_(including_sour)___healthy": "cherry_healthy",
    "Cherry___Powdery_mildew": "cherry_powdery_mildew",
    "Cherry___healthy": "cherry_healthy",
    "Corn___Cercospora_leaf_spot Gray_leaf_spot": "maize_gray_leaf_spot",
    "Corn___Common_rust": "maize_common_rust",
    "Corn___Northern_Leaf_Blight": "maize_northern_leaf_blight",
    "Corn___healthy": "maize_healthy",
    "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot": "maize_gray_leaf_spot",
    "Corn_(maize)___Common_rust_": "maize_common_rust",
    "Corn_(maize)___Northern_Leaf_Blight": "maize_northern_leaf_blight",
    "Corn_(maize)___healthy": "maize_healthy",
    "Grape___Black_rot": "grape_black_rot",
    "Grape___Esca_(Black_Measles)": "grape_esca",
    "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)": "grape_leaf_blight",
    "Grape___healthy": "grape_healthy",
    "Orange___Haunglongbing_(Citrus_greening)": "citrus_greening",
    "Peach___Bacterial_spot": "peach_bacterial_spot",
    "Peach___healthy": "peach_healthy",
    "Pepper,_bell___Bacterial_spot": "pepper_bacterial_spot",
    "Pepper,_bell___healthy": "pepper_healthy",
    "Potato___Early_blight": "potato_early_blight",
    "Potato___Late_blight": "potato_late_blight",
    "Potato___healthy": "potato_healthy",
    "Raspberry___healthy": "raspberry_healthy",
    "Soybean___healthy": "soybean_healthy",
    "Squash___Powdery_mildew": "squash_powdery_mildew",
    "Strawberry___Leaf_scorch": "strawberry_leaf_scorch",
    "Strawberry___healthy": "strawberry_healthy",
    "Tomato___Bacterial_spot": "tomato_bacterial_spot",
    "Tomato___Early_blight": "tomato_early_blight",
    "Tomato___Late_blight": "tomato_late_blight",
    "Tomato___Leaf_Mold": "tomato_leaf_mold",
    "Tomato___Septoria_leaf_spot": "tomato_septoria_leaf_spot",
    "Tomato___Spider_mites Two-spotted_spider_mite": "tomato_spider_mite",
    "Tomato___Target_Spot": "tomato_target_spot",
    "Tomato___Tomato_Yellow_Leaf_Curl_Virus": "tomato_yellow_leaf_curl",
    "Tomato___Tomato_mosaic_virus": "tomato_mosaic_virus",
    "Tomato___healthy": "tomato_healthy",
}

# TensorFlow Datasets order. dpdl-benchmark/plant_village uses these indexes.
PV_ORDER = [
    "Apple___Apple_scab",
    "Apple___Black_rot",
    "Apple___Cedar_apple_rust",
    "Apple___healthy",
    "Blueberry___healthy",
    "Cherry___healthy",
    "Cherry___Powdery_mildew",
    "Corn___Cercospora_leaf_spot Gray_leaf_spot",
    "Corn___Common_rust",
    "Corn___healthy",
    "Corn___Northern_Leaf_Blight",
    "Grape___Black_rot",
    "Grape___Esca_(Black_Measles)",
    "Grape___healthy",
    "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)",
    "Orange___Haunglongbing_(Citrus_greening)",
    "Peach___Bacterial_spot",
    "Peach___healthy",
    "Pepper,_bell___Bacterial_spot",
    "Pepper,_bell___healthy",
    "Potato___Early_blight",
    "Potato___healthy",
    "Potato___Late_blight",
    "Raspberry___healthy",
    "Soybean___healthy",
    "Squash___Powdery_mildew",
    "Strawberry___healthy",
    "Strawberry___Leaf_scorch",
    "Tomato___Bacterial_spot",
    "Tomato___Early_blight",
    "Tomato___healthy",
    "Tomato___Late_blight",
    "Tomato___Leaf_Mold",
    "Tomato___Septoria_leaf_spot",
    "Tomato___Spider_mites Two-spotted_spider_mite",
    "Tomato___Target_Spot",
    "Tomato___Tomato_mosaic_virus",
    "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
]

PLANDOC = {
    "apple scab leaf": "apple_scab",
    "apple rust leaf": "apple_cedar_rust",
    "apple leaf": "apple_healthy",
    "bell_pepper leaf spot": "pepper_bacterial_spot",
    "bell_pepper leaf": "pepper_healthy",
    "blueberry leaf": "blueberry_healthy",
    "cherry leaf": "cherry_healthy",
    "corn leaf blight": "maize_northern_leaf_blight",
    "corn gray leaf spot": "maize_gray_leaf_spot",
    "corn rust leaf": "maize_common_rust",
    "peach leaf": "peach_healthy",
    "potato leaf early blight": "potato_early_blight",
    "potato leaf late blight": "potato_late_blight",
    "raspberry leaf": "raspberry_healthy",
    "soyabean leaf": "soybean_healthy",
    "soybean leaf": "soybean_healthy",
    "squash powdery mildew leaf": "squash_powdery_mildew",
    "strawberry leaf": "strawberry_healthy",
    "tomato leaf bacterial spot": "tomato_bacterial_spot",
    "tomato early blight leaf": "tomato_early_blight",
    "tomato late blight leaf": "tomato_late_blight",
    "tomato leaf late blight": "tomato_late_blight",
    "tomato two spotted spider mites leaf": "tomato_spider_mite",
    "tomato leaf": "tomato_healthy",
    "tomato leaf mosaic virus": "tomato_mosaic_virus",
    "tomato leaf yellow virus": "tomato_yellow_leaf_curl",
    "tomato mold leaf": "tomato_leaf_mold",
    "tomato septoria leaf spot": "tomato_septoria_leaf_spot",
    "grape leaf black rot": "grape_black_rot",
    "grape leaf": "grape_healthy",
}

SOY = {
    "healthy": "soybean_healthy",
    "healthy plants": "soybean_healthy",
    "vein necrosis": "soybean_vein_necrosis",
    "dry leaf": "soybean_dry_leaf",
    "septoria brown spot": "soybean_septoria_brown_spot",
    "root images": "soybean_root",
    "bacterial leaf blight": "soybean_bacterial_blight",
}

FIELD_ALIAS = {
    "whitefly": "pest_whitefly",
}


def slug(name: str) -> str:
    text = name.strip().lower().replace("&", " and ")
    text = re.sub(r"[^a-z0-9]+", "_", text)
    return text.strip("_")


def unzip(name: str, dest: Path) -> None:
    archive = ZIPS / name
    marker = dest / ".extracted"
    if marker.exists():
        return
    dest.mkdir(parents=True, exist_ok=True)
    print(f"extract {name}", flush=True)
    with zipfile.ZipFile(archive) as handle:
        handle.extractall(dest)
    marker.write_text("ok\n")


def is_image(path: Path) -> bool:
    return path.suffix in IMAGE_EXT and path.is_file()


def walk_images(root: Path):
    for path in root.rglob("*"):
        if is_image(path):
            yield path


def add(rows: list[dict], source: str, label: str, path: Path, split: str = "") -> None:
    rows.append({"source": source, "label": label, "path": str(path), "split": split})


def image_path(folder: Path, stem: str, raw: bytes) -> Path:
    suffix = ".png" if raw.startswith(b"\x89PNG") else ".jpg"
    path = folder / f"{stem}{suffix}"
    if not path.exists():
        path.write_bytes(raw)
    return path


def from_plantvillage(rows: list[dict]) -> None:
    parquets = sorted(ZIPS.glob("plantvillage-train-*.parquet"))
    if len(parquets) >= 13:
        dest = EXTRACTED / "plantvillage"
        dest.mkdir(parents=True, exist_ok=True)
        for parquet in parquets:
            table = pq.read_table(parquet, columns=["image", "label"])
            labels = table.column("label").to_pylist()
            images = table.column("image").to_pylist()
            for index, (label, image) in enumerate(zip(labels, images)):
                raw = image.get("bytes") if isinstance(image, dict) else image
                if not raw:
                    continue
                folder = PV_ORDER[int(label)]
                path = image_path(dest, f"{parquet.stem}-{index}", raw)
                add(rows, "plantvillage", PV[folder], path)
        return
    dest = EXTRACTED / "plantvillage"
    unzip("plantvillage_no_aug.zip", dest)
    for path in walk_images(dest):
        folder = path.parent.name
        if folder.lower() in {"background", "backgrounds"}:
            continue
        label = PV.get(folder, "pv_" + slug(folder))
        add(rows, "plantvillage", label, path)


def from_soybean(rows: list[dict]) -> None:
    archive = ZIPS / "soybean.zip"
    if not archive.exists():
        print("soybean zip not ready, skipping", flush=True)
        return
    dest = EXTRACTED / "soybean"
    unzip("soybean.zip", dest)
    for path in walk_images(dest):
        folder = path.parent.name.strip().lower()
        label = SOY.get(folder)
        if label is None:
            label = "soybean_" + slug(folder)
        add(rows, "soybean", label, path)


def multicrop_classes(root: Path) -> list[str]:
    for name in ("classes.txt", "obj.names", "data.yaml", "data.yml"):
        for path in root.rglob(name):
            text = path.read_text(errors="replace")
            if name.endswith("yaml") or name.endswith("yml"):
                match = re.search(r"names:\s*\[([^\]]+)\]", text)
                if match:
                    return [part.strip().strip("'\"") for part in match.group(1).split(",")]
                names = re.findall(r"^\s*\d+\s*:\s*([A-Za-z0-9_ -]+)\s*$", text, re.M)
                if names:
                    return names
            else:
                lines = [line.strip() for line in text.splitlines() if line.strip()]
                if lines:
                    return lines
    return []


def yolo_label(txt: Path, names: list[str]) -> str | None:
    best_area = -1.0
    best = None
    for line in txt.read_text(errors="replace").splitlines():
        parts = line.split()
        if len(parts) < 5:
            continue
        try:
            class_id = int(float(parts[0]))
            area = float(parts[3]) * float(parts[4])
        except ValueError:
            continue
        if area > best_area and 0 <= class_id < len(names):
            best_area = area
            best = names[class_id]
    return best


def filename_label(name: str) -> str | None:
    stem = Path(name).name
    match = re.match(r"(.+?)-\d+-_jpe?g\.rf\.", stem, re.I)
    if not match:
        return None
    return match.group(1).strip(" _-")


def field_id(raw: str) -> str:
    key = slug(raw)
    return FIELD_ALIAS.get(key, "field_" + key)


def from_multicrop(rows: list[dict]) -> None:
    archive = ZIPS / "multicrop.zip"
    if not archive.exists():
        print("multicrop zip not ready, skipping", flush=True)
        return
    dest = EXTRACTED / "multicrop"
    unzip("multicrop.zip", dest)
    names = multicrop_classes(dest)
    print(f"multicrop class file entries: {len(names)}", flush=True)
    for path in walk_images(dest):
        raw = filename_label(path.name)
        if raw is None and names:
            txt = path.with_suffix(".txt")
            if txt.exists():
                raw = yolo_label(txt, names)
        if not raw:
            continue
        add(rows, "multicrop", field_id(raw), path)


def plantdoc_label(value) -> str:
    if isinstance(value, bytes):
        value = value.decode()
    text = str(value).strip().lower()
    return PLANDOC.get(text, "plantdoc_" + slug(text))


def from_plantdoc(rows: list[dict]) -> None:
    dest = EXTRACTED / "plantdoc"
    dest.mkdir(parents=True, exist_ok=True)
    files = sorted(ZIPS.glob("plantdoc-train-*.parquet"))
    if not files:
        raise SystemExit("PlantDoc parquet files are missing")
    seen = 0
    for parquet in files:
        table = pq.read_table(parquet, columns=["image", "class_label", "split"])
        labels = table.column("class_label").to_pylist()
        splits = table.column("split").to_pylist()
        images = table.column("image").to_pylist()
        for index, (label, split, image) in enumerate(zip(labels, splits, images)):
            raw = image.get("bytes") if isinstance(image, dict) else image
            if not raw:
                continue
            path = image_path(dest, f"{parquet.stem}-{index}", raw)
            official = str(split or "").strip().lower()
            if official not in {"train", "test"}:
                official = ""
            add(rows, "plantdoc", plantdoc_label(label), path, official)
            seen += 1
    print(f"plantdoc images {seen}", flush=True)


def main() -> None:
    rows: list[dict] = []
    from_plantvillage(rows)
    from_soybean(rows)
    from_multicrop(rows)
    from_plantdoc(rows)
    counts: dict[str, int] = defaultdict(int)
    sources: dict[str, int] = defaultdict(int)
    for row in rows:
        counts[row["label"]] += 1
        sources[row["source"]] += 1
    labels = sorted(counts)
    manifest = DATA / "manifest.csv"
    with manifest.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["source", "label", "path", "split"])
        writer.writeheader()
        writer.writerows(rows)
    (DATA / "classes.json").write_text(json.dumps(labels, indent=2) + "\n")
    summary = {
        "images": len(rows),
        "classes": len(labels),
        "by_source": dict(sources),
        "by_class": dict(sorted(counts.items())),
    }
    (DATA / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({"images": len(rows), "classes": len(labels), "by_source": dict(sources)}, indent=2))


if __name__ == "__main__":
    main()
