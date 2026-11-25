"""Turn the downloaded archives into one image manifest.

Overlapping diseases share one class id with kb/labels.json so a later
EfficientNet export can line up with the Pi label order. Crops that are not
in that list keep their own ids. The multi-crop zip uses the YOLO class names
in data.yaml, which already include the crop. The insect parquet set is left
out: its labels are bare integers and the train, validation, and test files
do not share one class list.
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
    "chilli_whitefly": "pest_whitefly",
}

CASSAVA = {
    0: "cassava_bacterial_blight",
    1: "cassava_brown_streak",
    2: "cassava_green_mottle",
    3: "cassava_mosaic",
    4: "cassava_healthy",
}

# Brown spot is not brown streak, and green mite is not green mottle.
CCMT = {
    ("cassava", "bacterial blight"): "cassava_bacterial_blight",
    ("cassava", "brown spot"): "cassava_brown_spot",
    ("cassava", "green mite"): "cassava_green_mite",
    ("cassava", "healthy"): "cassava_healthy",
    ("cassava", "mosaic"): "cassava_mosaic",
    ("maize", "fall armyworm"): "pest_fall_armyworm",
    ("maize", "grasshoper"): "pest_grasshopper",
    ("maize", "healthy"): "maize_healthy",
    ("maize", "leaf beetle"): "pest_leaf_beetle",
    ("maize", "leaf blight"): "maize_leaf_blight",
    ("maize", "leaf spot"): "maize_leaf_spot",
    ("maize", "streak virus"): "maize_streak_virus",
    ("tomato", "healthy"): "tomato_healthy",
    ("tomato", "leaf blight"): "tomato_leaf_blight",
    ("tomato", "leaf curl"): "tomato_yellow_leaf_curl",
    ("tomato", "septoria leaf spot"): "tomato_septoria_leaf_spot",
    ("tomato", "verticulium wilt"): "tomato_verticillium_wilt",
    ("cashew", "anthracnose"): "cashew_anthracnose",
    ("cashew", "gumosis"): "cashew_gummosis",
    ("cashew", "healthy"): "cashew_healthy",
    ("cashew", "leaf miner"): "cashew_leaf_miner",
    ("cashew", "red rust"): "cashew_red_rust",
}

RICE_ALIAS = {
    "bacterialblight": "rice_bacterial_blight",
    "bacterial_leaf_blight": "rice_bacterial_blight",
    "blast": "rice_blast",
    "brownspot": "rice_brown_spot",
    "brown_spot": "rice_brown_spot",
    "tungro": "rice_tungro",
    "normal": "rice_healthy",
}

EXTRA = DATA / "extra"


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


def reuse_source(rows: list[dict], source: str, minimum: int) -> bool:
    manifest = DATA / "manifest.csv"
    if not manifest.exists():
        return False
    kept = 0
    with manifest.open() as handle:
        for row in csv.DictReader(handle):
            if row["source"] != source or not Path(row["path"]).is_file():
                continue
            add(rows, source, row["label"], Path(row["path"]), row.get("split") or "")
            kept += 1
    if kept < minimum:
        if kept:
            del rows[-kept:]
        return False
    print(f"{source} reused {kept}", flush=True)
    return True


def from_plantvillage(rows: list[dict]) -> None:
    parquets = sorted(ZIPS.glob("plantvillage-train-*.parquet"))
    if len(parquets) >= 13 and reuse_source(rows, "plantvillage", 50000):
        return
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


def soy_key(folder: str) -> str:
    text = folder.strip().lower().replace("_", " ")
    text = re.sub(r"^\d+\s*[\.\-_]+\s*", "", text)
    return re.sub(r"\s+", " ", text).strip()


def from_soybean(rows: list[dict]) -> None:
    archive = ZIPS / "soybean.zip"
    if not archive.exists():
        print("soybean zip not ready, skipping", flush=True)
        return
    dest = EXTRACTED / "soybean"
    unzip("soybean.zip", dest)
    seen = 0
    unknown: set[str] = set()
    for path in walk_images(dest):
        # "Leaf Images All (Raw)" repeats the class folders.
        if any("raw" in part.lower() for part in path.parts):
            continue
        label = None
        for part in reversed(path.parts):
            key = soy_key(part)
            label = SOY.get(key)
            if label is not None:
                break
        if label is None:
            unknown.add(path.parent.name)
            continue
        add(rows, "soybean", label, path)
        seen += 1
    print(f"soybean images {seen}", flush=True)
    if unknown:
        print("soybean unlabeled " + ", ".join(sorted(unknown)), flush=True)


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
    return FIELD_ALIAS.get(key, key)


def official_split(path: Path) -> str:
    parts = {part.lower() for part in path.parts}
    if parts & {"test", "testing", "valid", "val", "validation"}:
        return "test"
    if parts & {"train", "training"}:
        return "train"
    return ""


def label_file(path: Path) -> Path:
    if path.parent.name.lower() == "images":
        return path.parent.parent / "labels" / f"{path.stem}.txt"
    return path.with_suffix(".txt")


def from_multicrop(rows: list[dict]) -> None:
    archive = ZIPS / "multicrop.zip"
    if not archive.exists():
        print("multicrop zip not ready, skipping", flush=True)
        return
    dest = EXTRACTED / "multicrop"
    unzip("multicrop.zip", dest)
    names = multicrop_classes(dest)
    print(f"multicrop class file entries: {len(names)}", flush=True)
    seen = 0
    skipped = 0
    for path in walk_images(dest):
        raw = None
        txt = label_file(path)
        labeled = txt.exists()
        if names and labeled:
            raw = yolo_label(txt, names)
        elif not labeled:
            raw = filename_label(path.name)
            if raw and raw.lower() == "img":
                raw = None
        if not raw:
            skipped += 1
            continue
        add(rows, "multicrop", field_id(raw), path, official_split(path))
        seen += 1
    print(f"multicrop images {seen} skipped {skipped}", flush=True)


def plantdoc_label(value) -> str:
    if isinstance(value, bytes):
        value = value.decode()
    text = str(value).strip().lower()
    return PLANDOC.get(text, "plantdoc_" + slug(text))


def from_plantdoc(rows: list[dict]) -> None:
    if reuse_source(rows, "plantdoc", 2500):
        return
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


def from_cassava(rows: list[dict]) -> None:
    root = EXTRA / "cassava"
    table = root / "train.csv"
    if not table.exists():
        print("cassava not ready, skipping", flush=True)
        return
    images = {}
    for path in (root / "train_images").rglob("*"):
        if is_image(path):
            images[path.name] = path
    missing = 0
    seen = 0
    with table.open() as handle:
        for row in csv.DictReader(handle):
            path = images.get(row["image_id"])
            if path is None:
                missing += 1
                continue
            add(rows, "cassava", CASSAVA[int(row["label"])], path)
            seen += 1
    print(f"cassava images {seen} missing {missing}", flush=True)


def from_ccmt(rows: list[dict]) -> None:
    root = EXTRA / "ccmt" / "CCMTDataset"
    if not root.exists():
        print("ccmt not ready, skipping", flush=True)
        return
    seen = 0
    for path in walk_images(root):
        crop = path.parent.parent.name.strip().lower()
        kind = path.parent.name.strip().lower()
        label = CCMT.get((crop, kind))
        if label is None:
            label = slug(crop) + "_" + slug(kind)
        add(rows, "ccmt", label, path)
        seen += 1
    print(f"ccmt images {seen}", flush=True)


def parquet_class_names(path: Path) -> list[str]:
    raw = pq.ParquetFile(path).schema_arrow.metadata[b"huggingface"]
    info = json.loads(raw)
    names = info["info"]["features"]["label"].get("names")
    if not names:
        raise ValueError(f"{path.name} has no class names")
    return list(names)


def rice_label(name: str) -> str:
    key = slug(name)
    return RICE_ALIAS.get(key, "rice_" + key)


def from_rice_parquet(rows: list[dict], source: str) -> None:
    if reuse_source(rows, source, 5000):
        return
    folder = EXTRA / source
    files = sorted(folder.glob("*.parquet"))
    if not files:
        print(f"{source} not ready, skipping", flush=True)
        return
    names = parquet_class_names(files[0])
    dest = EXTRACTED / source
    dest.mkdir(parents=True, exist_ok=True)
    seen = 0
    for parquet in files:
        handle = pq.ParquetFile(parquet)
        for group in range(handle.num_row_groups):
            table = handle.read_row_group(group, columns=["image", "label"])
            labels = table.column("label").to_pylist()
            images = table.column("image").to_pylist()
            for index, (label, image) in enumerate(zip(labels, images)):
                raw = image.get("bytes") if isinstance(image, dict) else image
                if not raw:
                    continue
                path = image_path(dest, f"{parquet.stem}-g{group}-{index}", raw)
                add(rows, source, rice_label(names[int(label)]), path)
                seen += 1
            print(f"{source} {parquet.name} group {group} kept {seen}", flush=True)
    print(f"{source} images {seen}", flush=True)


def main() -> None:
    rows: list[dict] = []
    from_plantvillage(rows)
    from_soybean(rows)
    from_multicrop(rows)
    from_plantdoc(rows)
    from_cassava(rows)
    from_ccmt(rows)
    from_rice_parquet(rows, "rice-india")
    from_rice_parquet(rows, "paddy-doctor")
    print(
        "insect-pests skipped: labels are integers with no names, and the splits do not share one class list",
        flush=True,
    )
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
