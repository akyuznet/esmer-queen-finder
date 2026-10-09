# ESMER QUEEN FINDER

A free, offline Android app that spots the queen bee live in the camera preview
while you hold a frame up to the phone. No subscription, no account, no internet.

Detection runs entirely on the phone with a small YOLO model on LiteRT
(TensorFlow Lite). A queen must be seen in several consecutive frames before the
app vibrates and beeps, which keeps false alarms from drones and workers down.

## Project layout

```
app/                         Android app (Kotlin, Jetpack Compose, CameraX, LiteRT)
  src/main/assets/           queen.tflite + labels.txt (produced by ml/train.py)
  src/main/java/com/esmer/queenfinder/
    detection/               model loading, letterbox, decode, NMS, track filter
    camera/ ui/camera/       CameraX preview, overlay, camera screen
    alert/                   vibration and beep
    data/                    settings (DataStore)
ml/                          dataset prep, training, export and scoring scripts
```

## Build the app

Open the project in Android Studio (AGP 9.3, Kotlin 2.2) or run:

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app expects `app/src/main/assets/queen.tflite` and `labels.txt`. Any
Ultralytics YOLO export works (YOLOv5, v8, 11, 26; float32 or INT8). The label
file must contain `queen`; `drone` is optional but recommended.

## Train the model

```
cd ml
python -m pip install -r requirements.txt
python prepare_data.py --api-key YOUR_ROBOFLOW_KEY   # downloads and merges public datasets
python train.py --epochs 100                         # trains yolo11n, exports INT8 416 px to assets/
python score.py --model ../app/src/main/assets/queen.tflite --data data/merged/data.yaml
```

Datasets are pulled from Roboflow Universe; check each dataset's license on its
page before using it. Add your own hive photos to `ml/data/merged` for the second
training round.

## License

AGPL-3.0. See `LICENSE`. Ultralytics YOLO is AGPL-3.0, which is why this app,
its model and its training scripts are published under the same license.
