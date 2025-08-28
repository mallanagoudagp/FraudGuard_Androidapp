"""
Train a tiny TensorFlow Autoencoder on provided CSV(s) and export TFLite + scaler + threshold.

CSV format expected (no header, one gesture per row):
gesture,duration_ms,total_distance,avg_velocity,peak_velocity,avg_pressure,peak_pressure,path_deviation,direction_changes,jitter

Examples:
	python ml/touch/train_autoencoder_tf.py --csv data/normal_touch_features_*.csv --epochs 3 --out models/touch_ae_tflite_tf
	# Or simply run without --csv to use the default pattern data/normal_touch_features_*.csv
"""
import argparse
import glob
import json
import os
import sys
from typing import List

import numpy as np
import pandas as pd
import tensorflow as tf
# Support both "python -m ml.touch.train_autoencoder_tf" and direct file execution
try:
	from .gesture_map import get_default_map  # type: ignore
except Exception:  # pragma: no cover - runtime fallback for direct script exec
	import os as _os, sys as _sys
	_sys.path.append(_os.path.abspath(_os.path.join(_os.path.dirname(__file__), "..", "..")))
	from ml.touch.gesture_map import get_default_map  # type: ignore
import keras as keras

def _load_one_csv(path: str) -> np.ndarray:
	# Read rows; keep only those whose first col matches a known gesture
	df = pd.read_csv(path, header=None, skip_blank_lines=True)
	gmap = get_default_map()
	df = df[df[0].astype(str).isin(gmap.keys())].copy()
	if df.empty:
		return np.zeros((0, 0), dtype=np.float32)
	df.columns = [
		"gesture", "duration_ms", "total_distance", "avg_velocity", "peak_velocity",
		"avg_pressure", "peak_pressure", "path_deviation", "direction_changes", "jitter"
	]
	df["gesture_num"] = df["gesture"].map(gmap).astype(float)
	feats = [
		"gesture_num", "duration_ms", "total_distance", "avg_velocity", "peak_velocity",
		"avg_pressure", "peak_pressure", "path_deviation", "direction_changes", "jitter"
	]
	X = df[feats].astype(np.float32).to_numpy()
	return X


def load_csvs(patterns: List[str]) -> np.ndarray:
	files: List[str] = []
	for p in patterns:
		files.extend(glob.glob(p))
	files = sorted(set(files))
	if not files:
		raise FileNotFoundError("No CSV files matched: " + ", ".join(patterns))
	arrays = []
	for f in files:
		X = _load_one_csv(f)
		if X.size > 0:
			arrays.append(X)
	if not arrays:
		raise ValueError("No valid rows found in the matched CSV files.")
	Xall = np.vstack(arrays)
	return Xall

def build_model(n):
	inp = keras.Input(shape=(n,), name="in")
	x = keras.layers.Dense(max(8, n // 2), activation="relu")(inp)
	x = keras.layers.Dense(n, activation=None, name="recon")(x)
	model = keras.Model(inp, x)
	model.compile(optimizer=keras.optimizers.Adam(1e-3), loss="mse")
	return model


def main(argv=None):
	ap = argparse.ArgumentParser()
	ap.add_argument(
		"--csv",
		nargs="+",
		required=False,
		default=None,
		help="CSV file(s) or globs of normal data (default: data/normal_touch_features_*.csv)",
	)
	ap.add_argument("--epochs", type=int, default=5)
	ap.add_argument("--batch", type=int, default=64)
	ap.add_argument("--out", default="models/touch_ae_tflite_tf")
	ap.add_argument("--threshold_k", type=float, default=3.0, help="std dev multiplier for threshold")
	args = ap.parse_args(argv)

	os.makedirs(args.out, exist_ok=True)

	patterns = args.csv if args.csv is not None else [
		os.path.join("data", "normal_touch_features_*.csv"),
		os.path.join("data", "normal_touch_features_*.CSV"),
	]

	X = load_csvs(patterns)
	n = X.shape[1]
	mean = X.mean(axis=0)
	std = X.std(axis=0)
	std[std == 0] = 1.0
	Xs = (X - mean) / std

	model = build_model(n)
	model.fit(Xs, Xs, epochs=args.epochs, batch_size=args.batch, verbose=2)

	# Reconstruction and threshold
	pred = model.predict(Xs, batch_size=args.batch, verbose=0)
	mse = ((Xs - pred) ** 2).mean(axis=1)
	thr = float(np.mean(mse) + args.threshold_k * np.std(mse))

	# Write scaler and threshold
	with open(os.path.join(args.out, "scaler.json"), "w") as f:
		json.dump({"mean": mean.tolist(), "scale": std.tolist()}, f)
	with open(os.path.join(args.out, "threshold.json"), "w") as f:
		json.dump({"threshold": thr}, f)

	# Export TFLite directly
	converter = tf.lite.TFLiteConverter.from_keras_model(model)
	tfl = converter.convert()
	tfl_path = os.path.join(args.out, "touch_ae.tflite")
	with open(tfl_path, "wb") as f:
		f.write(tfl)

	with open(os.path.join(args.out, "report.txt"), "w") as f:
		f.write(f"n={n}\nthreshold={thr}\ntrain_samples={len(X)}\n")

	print(f"Wrote: {tfl_path}")


if __name__ == "__main__":
	main()

