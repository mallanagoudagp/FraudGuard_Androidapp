# Configuration (config/)

Primary file: `app.properties`

Keys:

- `enable_nn_scoring` (true|false): enable Python bridge for TouchAgent scoring
- `python_exe`: path to Python interpreter used by the bridge
- `model_dir`: directory with model artifacts used by the bridge
- `nn_mode` (oneshot|persistent): scoring mode
- `nn_timeout_ms`: timeout for a scoring call (0 = no timeout)
- `fusion_weights`: comma-separated weights for touch vs typing, e.g., `0.6,0.4`
