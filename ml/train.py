"""Train the queen/drone detector and export it for the Android app.

Usage:
    python train.py --data data/merged/data.yaml --epochs 100
    python train.py --export-only runs/detect/queen/weights/best.pt

The export writes app/src/main/assets/queen.tflite and labels.txt.
"""
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from ultralytics import YOLO

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parent / "app" / "src" / "main" / "assets"


def train(data: str, model: str, epochs: int, imgsz: int, batch: int) -> Path:
    m = YOLO(model)
    m.train(
        data=data,
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        project=str(ROOT / "runs" / "detect"),
        name="queen",
        exist_ok=True,
        mosaic=1.0,
        hsv_h=0.02,
        hsv_s=0.6,
        hsv_v=0.5,
        fliplr=0.5,
        flipud=0.5,
        degrees=15,
        scale=0.5,
        patience=30,
    )
    return ROOT / "runs" / "detect" / "queen" / "weights" / "best.pt"


def export(weights: Path, imgsz: int, int8: bool, data: str | None) -> Path:
    m = YOLO(str(weights))
    kwargs = dict(imgsz=imgsz, nms=False)
    if int8:
        kwargs["int8"] = True
        if data:
            kwargs["data"] = data
    out = Path(m.export(format="tflite", **kwargs))
    # Ultralytics writes a folder of variants; pick the one we asked for.
    if out.is_dir():
        candidates = sorted(out.glob("*_int8.tflite" if int8 else "*_float32.tflite"))
        if not candidates:
            candidates = sorted(out.glob("*.tflite"))
        out = candidates[0]
    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy(out, ASSETS / "queen.tflite")
    names = m.names if isinstance(m.names, dict) else dict(enumerate(m.names))
    (ASSETS / "labels.txt").write_text("\n".join(names[i] for i in sorted(names)) + "\n")
    print(f"wrote {ASSETS / 'queen.tflite'} ({(ASSETS / 'queen.tflite').stat().st_size / 1e6:.1f} MB)")
    return ASSETS / "queen.tflite"


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(ROOT / "data" / "merged" / "data.yaml"))
    ap.add_argument("--model", default="yolo11n.pt")
    ap.add_argument("--epochs", type=int, default=100)
    ap.add_argument("--imgsz", type=int, default=640, help="training image size")
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--export-imgsz", type=int, default=416, help="on-device input size")
    ap.add_argument("--no-int8", action="store_true")
    ap.add_argument("--export-only", metavar="WEIGHTS")
    args = ap.parse_args()

    weights = Path(args.export_only) if args.export_only else train(
        args.data, args.model, args.epochs, args.imgsz, args.batch
    )
    export(weights, args.export_imgsz, int8=not args.no_int8, data=args.data)
