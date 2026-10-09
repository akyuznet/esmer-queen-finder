"""Download public Roboflow datasets, remap their classes to {queen, drone}, and merge
them into one YOLO dataset at ml/data/merged with a source-wise held-out split.

Usage:
    python prepare_data.py --api-key YOUR_ROBOFLOW_KEY

Each entry in SOURCES is (workspace, project, version). Check the license on each
dataset page before use and keep this list in the repo as the attribution record.
"""
from __future__ import annotations

import argparse
import hashlib
import random
import shutil
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "data" / "raw"
MERGED = ROOT / "data" / "merged"

# (workspace, project, version). Versions can be adjusted on the dataset page.
SOURCES = [
    ("sabian", "beesbeesbees", 1),
    ("beequeen-detector", "beequeendetector-ta7ig", 1),
    ("beedataset", "honey-bee-detection-model-zgjnb-dmdag", 1),
    ("sharedws", "queen_bee-kcfnv", 1),
    ("beekeeper4", "queen_drone_bee-detection", 1),
    ("qualitypineapple", "queen-class", 1),
]

TARGET_CLASSES = ["queen", "drone"]

# Any source class name containing one of these substrings maps to the target class.
CLASS_ALIASES = {
    "queen": ["queen"],
    "drone": ["drone"],
}


def download(api_key: str) -> list[Path]:
    from roboflow import Roboflow

    rf = Roboflow(api_key=api_key)
    out = []
    for ws, proj, ver in SOURCES:
        dest = RAW / f"{ws}__{proj}__v{ver}"
        if dest.exists():
            print(f"skip (exists): {dest.name}")
            out.append(dest)
            continue
        print(f"downloading {ws}/{proj} v{ver}")
        try:
            ds = rf.workspace(ws).project(proj).version(ver).download("yolov8", location=str(dest))
            out.append(Path(ds.location))
        except Exception as e:  # noqa: BLE001
            print(f"  failed: {e}")
    return out


def class_map(names: list[str]) -> dict[int, int]:
    mapping = {}
    for i, n in enumerate(names):
        low = n.lower()
        for ti, tname in enumerate(TARGET_CLASSES):
            if any(a in low for a in CLASS_ALIASES[tname]):
                mapping[i] = ti
                break
    return mapping


def file_hash(p: Path) -> str:
    return hashlib.md5(p.read_bytes()).hexdigest()


def merge(sources: list[Path], holdout: float, seed: int) -> None:
    random.seed(seed)
    for split in ("train", "val", "test"):
        (MERGED / split / "images").mkdir(parents=True, exist_ok=True)
        (MERGED / split / "labels").mkdir(parents=True, exist_ok=True)

    seen = set()
    counts = {"train": 0, "val": 0, "test": 0, "dropped_empty": 0, "dup": 0}
    for src in sources:
        data_yaml = next(src.rglob("data.yaml"), None)
        if data_yaml is None:
            print(f"no data.yaml in {src}")
            continue
        names = yaml.safe_load(data_yaml.read_text())["names"]
        cmap = class_map(list(names))
        print(f"{src.name}: classes {names} -> {cmap}")
        # Hold out one whole source as test when requested, else split per source.
        for img in src.rglob("*.jpg"):
            if "images" not in img.parts:
                continue
            h = file_hash(img)
            if h in seen:
                counts["dup"] += 1
                continue
            seen.add(h)
            lbl = img.parent.parent / "labels" / (img.stem + ".txt")
            lines = []
            if lbl.exists():
                for line in lbl.read_text().splitlines():
                    parts = line.split()
                    if len(parts) < 5:
                        continue
                    ci = int(parts[0])
                    if ci in cmap:
                        lines.append(" ".join([str(cmap[ci])] + parts[1:5]))
            if not lines:
                counts["dropped_empty"] += 1
                continue
            r = random.random()
            split = "test" if r < holdout else ("val" if r < holdout * 2 else "train")
            name = f"{src.name}__{img.stem}"
            shutil.copy(img, MERGED / split / "images" / f"{name}.jpg")
            (MERGED / split / "labels" / f"{name}.txt").write_text("\n".join(lines) + "\n")
            counts[split] += 1

    (MERGED / "data.yaml").write_text(
        yaml.safe_dump(
            {
                "path": str(MERGED),
                "train": "train/images",
                "val": "val/images",
                "test": "test/images",
                "names": TARGET_CLASSES,
            }
        )
    )
    print(counts)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--api-key", required=True, help="Roboflow API key (free account)")
    ap.add_argument("--holdout", type=float, default=0.1, help="fraction for test and for val")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    srcs = download(args.api_key)
    merge(srcs, args.holdout, args.seed)
