#!/usr/bin/env python3
import sys
import json
import argparse

# Minimal, dependency-free persistent scorer.
# For each line of JSON on stdin, prints one line of JSON result on stdout.

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

    for line in sys.stdin:
        try:
            line = line.strip()
            if not line:
                continue
            data = json.loads(line)
            mse = compute_mse(data)
            threshold = 1.0
            score = max(0.0, min(1.0, mse / threshold))
            out = {"ok": True, "mse": mse, "threshold": threshold, "score": score}
            sys.stdout.write(json.dumps(out) + "\n")
            sys.stdout.flush()
        except Exception as e:
            sys.stdout.write(json.dumps({"ok": False, "error": str(e)}) + "\n")
            sys.stdout.flush()


if __name__ == '__main__':
    main()
