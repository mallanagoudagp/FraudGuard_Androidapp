# Android demo (android-app/)

Open this folder in Android Studio and run the `app` configuration.

Steps:
- Ensure `local.properties` has `sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.
- Let Gradle sync. Build > Make Project.
- Run on device/emulator. Check Logcat tag `FraudGuard` for: `AE score=..., mse=..., thr=...`.

Assets:
- `app/src/main/assets/touch_ae.tflite`: TFLite model (placeholder expected format [1,N]).
- `app/src/main/assets/scaler.json`: {"mean": [...], "scale": [...]}.
- `app/src/main/assets/threshold.json`: {"threshold": 0.05}.

Train/Export (desktop): use `ml/touch/train_autoencoder_tf.py` then copy artifacts into assets.

CLI users: Gradle 8.x, AGP 8.x. Task: `:app:assembleDebug`.
