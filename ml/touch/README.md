# ML bridge (ml/touch)

Scripts implementing a minimal, dependency-free scorer used by the Java bridge.

- `score_once_sklearn.py`: reads one JSON object from stdin and writes a JSON result.
- `serve_sklearn.py`: persistent mode; reads JSON lines from stdin and writes JSON lines to stdout.

Both versions avoid external ML frameworks. You can replace them with real sklearn/onnxruntime code if desired.
