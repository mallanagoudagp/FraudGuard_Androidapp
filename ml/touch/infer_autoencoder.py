"""
General inference script for the Touch autoencoder.

Supports two modes:
- sklearn: loads model.joblib + scaler.joblib + threshold.json from a directory
- tflite:  loads touch_ae.tflite + scaler.json + threshold.json from given paths

Input CSV format (no header, one gesture per row):
gesture,duration_ms,total_distance,avg_velocity,peak_velocity,avg_pressure,peak_pressure,path_deviation,direction_changes,jitter

Example:
SWIPE,250,137.11946,0.5484778,0.62185323,0.70933867,0.9875791,0.4828847,0,0.35521233

Output:
- Prints summary stats to stdout
- Optionally writes a CSV with appended columns: mse,score

Usage examples:
python ml/touch/infer_autoencoder.py --csv data\normal_touch_features_20250824_152548.csv --sklearn_dir models\touch_ae_sklearn --out scores_sklearn.csv
python ml\touch\infer_autoencoder.py --csv data\normal_touch_features_20250824_152548.csv --tflite models\touch_ae_tflite_tf\touch_ae.tflite --scaler models\touch_ae_tflite_tf\scaler.json --threshold models\touch_ae_tflite_tf\threshold.json --out scores_tflite.csv
"""
import argparse
import json
import os
import sys
from typing import Tuple, List

import numpy as np
import pandas as pd

# Support both module and direct script execution
try:
    from .gesture_map import get_default_map  # type: ignore
except Exception:
    import os as _os, sys as _sys
    _sys.path.append(_os.path.abspath(_os.path.join(_os.path.dirname(__file__), "..", "..")))
    from ml.touch.gesture_map import get_default_map  # type: ignore


def load_csv(csv_path: str) -> Tuple[np.ndarray, List[str]]:
    # Robust load: ignore blank lines; filter to rows that start with known gestures
    df = pd.read_csv(csv_path, header=None, skip_blank_lines=True)
    gesture_map = get_default_map()
    df = df[df[0].astype(str).isin(gesture_map.keys())].copy()
    if df.empty:
        raise ValueError("No valid rows found. Ensure first column is one of: " + ", ".join(gesture_map.keys()))
    df.columns = [
        "gesture", "duration_ms", "total_distance", "avg_velocity", "peak_velocity",
        "avg_pressure", "peak_pressure", "path_deviation", "direction_changes", "jitter"
    ]
    df["gesture_num"] = df["gesture"].map(gesture_map).astype(float)
    feats = [
        "gesture_num", "duration_ms", "total_distance", "avg_velocity", "peak_velocity",
        "avg_pressure", "peak_pressure", "path_deviation", "direction_changes", "jitter"
    ]
    X = df[feats].astype(float).to_numpy()
    return X, feats


def load_json(path: str):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_threshold(threshold_path: str) -> float:
    data = load_json(threshold_path)
    thr = data.get("threshold", None)
    if thr is None:
        raise ValueError(f"threshold.json missing 'threshold' key: {threshold_path}")
    return float(thr)


def standardize_with_json(X: np.ndarray, scaler_json_path: str) -> np.ndarray:
    data = load_json(scaler_json_path)
    mean = np.asarray(data["mean"], dtype=np.float32)
    scale = np.asarray(data["scale"], dtype=np.float32)
    if mean.shape[0] != X.shape[1] or scale.shape[0] != X.shape[1]:
        raise ValueError(f"Scaler dim {mean.shape[0]} does not match input dim {X.shape[1]}")
    scale_safe = np.where(scale == 0.0, 1.0, scale)
    return (X - mean) / scale_safe


def try_import_joblib():
    try:
        import joblib  # noqa
        return True
    except Exception:
        return False


def score_with_sklearn(X: np.ndarray, model_dir: str) -> Tuple[np.ndarray, float]:
    if not try_import_joblib():
        raise RuntimeError("joblib not installed. pip install joblib scikit-learn")
    import joblib

    model_path = os.path.join(model_dir, "model.joblib")
    scaler_path_joblib = os.path.join(model_dir, "scaler.joblib")
    scaler_path_json = os.path.join(model_dir, "scaler.json")
    threshold_path = os.path.join(model_dir, "threshold.json")

    if os.path.isfile(scaler_path_joblib):
        scaler = joblib.load(scaler_path_joblib)
        Xs = scaler.transform(X)
    elif os.path.isfile(scaler_path_json):
        Xs = standardize_with_json(X, scaler_path_json)
    else:
        raise FileNotFoundError(f"No scaler found at {scaler_path_joblib} or {scaler_path_json}")

    model = joblib.load(model_path)
    recon = model.predict(Xs)
    if recon.shape != Xs.shape:
        raise ValueError(f"Model output shape {recon.shape} != input shape {Xs.shape}. "
                         "Expected an autoencoder reconstructing inputs.")

    mse = ((Xs - recon) ** 2).mean(axis=1)
    thr = load_threshold(threshold_path)
    return mse, thr


def get_tflite_interpreter(tflite_path: str):
    try:
        from tflite_runtime.interpreter import Interpreter  # type: ignore
        return Interpreter(model_path=tflite_path)
    except Exception:
        try:
            from tensorflow.lite.python.interpreter import Interpreter  # type: ignore
            return Interpreter(model_path=tflite_path)
        except Exception as e:
            raise RuntimeError("Neither tflite_runtime nor TensorFlow Lite is available.") from e


def score_with_tflite(X: np.ndarray, tflite_path: str, scaler_path: str, threshold_path: str) -> Tuple[np.ndarray, float]:
    # Standardize
    if scaler_path.endswith(".json"):
        Xs = standardize_with_json(X, scaler_path)
    else:
        if not try_import_joblib():
            raise RuntimeError("joblib not installed. pip install joblib scikit-learn")
        import joblib
        scaler = joblib.load(scaler_path)
        Xs = scaler.transform(X)

    # TFLite inference
    itp = get_tflite_interpreter(tflite_path)
    itp.allocate_tensors()
    inp = itp.get_input_details()[0]
    out = itp.get_output_details()[0]

    # Ensure input dims match
    d = Xs.shape[1]
    expected = inp["shape"][1] if len(inp["shape"]) == 2 else d
    if expected != d:
        raise ValueError(f"TFLite model expects dim={expected}, but got {d} features.")

    # Run in batches
    batch_size = 128
    mse_list = []
    for i in range(0, Xs.shape[0], batch_size):
        xb = Xs[i:i+batch_size].astype(np.float32)
        itp.resize_tensor_input(inp["index"], [xb.shape[0], d])
        itp.allocate_tensors()
        itp.set_tensor(inp["index"], xb)
        itp.invoke()
        recon = itp.get_tensor(out["index"]).astype(np.float32)
        mse = ((xb - recon) ** 2).mean(axis=1)
        mse_list.append(mse)
    mse = np.concatenate(mse_list, axis=0)

    thr = load_threshold(threshold_path)
    return mse, thr


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", required=True, help="Input CSV with gestures")
    ap.add_argument("--out", default=None, help="Optional output CSV with appended mse,score")
    group = ap.add_mutually_exclusive_group(required=True)
    group.add_argument("--sklearn_dir", help="Directory with model.joblib, scaler.(joblib|json), threshold.json")
    group.add_argument("--tflite", help="Path to .tflite model for inference")
    ap.add_argument("--scaler", help="Path to scaler.json or scaler.joblib (required for --tflite)")
    ap.add_argument("--threshold", help="Path to threshold.json (required for --tflite)")
    args = ap.parse_args()

    X, feats = load_csv(args.csv)

    if args.sklearn_dir:
        mse, thr = score_with_sklearn(X, args.sklearn_dir)
    else:
        if not args.scaler or not args.threshold:
            print("--scaler and --threshold are required with --tflite", file=sys.stderr)
            sys.exit(2)
        mse, thr = score_with_tflite(X, args.tflite, args.scaler, args.threshold)

    scores = np.minimum(1.0, mse / float(thr))
    print(f"Samples={len(scores)}  MSE mean={mse.mean():.6f} std={mse.std():.6f}  thr={thr:.6f}")
    print(f"Score mean={scores.mean():.4f}  >0.8={(scores>0.8).mean():.2%}  >0.5={(scores>0.5).mean():.2%}")

    if args.out:
        # Append mse and score to the original CSV rows
        df = pd.read_csv(args.csv, header=None, skip_blank_lines=True)
        gesture_map = get_default_map()
        df = df[df[0].astype(str).isin(gesture_map.keys())].copy()
        df["mse"] = mse
        df["score"] = scores
        df.to_csv(args.out, index=False, header=False)
        print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
