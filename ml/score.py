"""Score an exported .tflite on a folder of held-out field photos with YOLO labels.

Usage:
    python score.py --model ../app/src/main/assets/queen.tflite --data data/merged/data.yaml --split test
"""
from __future__ import annotations

import argparse

from ultralytics import YOLO

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--data", required=True)
    ap.add_argument("--split", default="test")
    ap.add_argument("--imgsz", type=int, default=416)
    args = ap.parse_args()
    m = YOLO(args.model)
    r = m.val(data=args.data, split=args.split, imgsz=args.imgsz, plots=False)
    print("mAP50:", r.box.map50, "mAP50-95:", r.box.map)
    for i, name in r.names.items():
        if i < len(r.box.p):
            print(f"{name:8s} P={r.box.p[i]:.3f} R={r.box.r[i]:.3f}")
