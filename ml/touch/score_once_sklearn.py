#!/usr/bin/env python3
import sys
import json
import argparse

# Minimal, dependency-free scorer for TouchAgent features.
# Reads one JSON object from stdin and prints a JSON result to stdout.
# This simulates a sklearn AE scorer without requiring external packages.

SCALES = {
    "duration_ms": 500.0,
    "total_distance": 300.0,
    "avg_velocity": 1.0,
    "peak_velocity": 5.0,
    "avg_pressure": 1.0,
    "peak_pressure": 1.0,
    "path_deviation": 5.0,
    "direction_changes": 5.0,
    "jitter": 3.0,
}


def compute_mse(features: dict) -> float:
    vals = []
    for k, scale in SCALES.items():
        v = features.get(k)
        if isinstance(v, (int, float)):
            try:
                vals.append((float(v) / scale) ** 2)
            except Exception:
                pass
    if not vals:
        return 0.0
    return sum(vals) / len(vals)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', type=str, required=False, default='models/touch_ae_sklearn')
    _ = parser.parse_args()

    try:
        payload = sys.stdin.read()
        data = json.loads(payload) if payload.strip() else {}
        mse = compute_mse(data)
        threshold = 1.0
        score = max(0.0, min(1.0, mse / threshold))
        out = {"ok": True, "mse": mse, "threshold": threshold, "score": score}
        sys.stdout.write(json.dumps(out))
        sys.stdout.flush()
    except Exception as e:
        sys.stdout.write(json.dumps({"ok": False, "error": str(e)}))
        sys.stdout.flush()


if __name__ == '__main__':
    main()
