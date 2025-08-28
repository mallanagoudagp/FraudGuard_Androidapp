## TypingAgent implementation

This document explains how the TypingAgent works in FraudGuard, what it measures, how it scores anomalies, and how to use/tune it.

### Overview

- Purpose: detect anomalous keystroke dynamics (timing/rhythm), privacy-first (no text captured).
- Inputs: per-keystroke events with timing and optional pressure.
- Output: normalized anomaly score [0–1] with human-readable explanations.

### Signals and features

- Dwell time: key down → key up interval.
- Flight time: previous key down → next key down interval.
- Backspace/Delete rate: proportion of correction keys in recent window.
- Paste-like bursts: very short inter-key intervals indicate automated input.

Windowing and baselines:
- Window size: last 50 values per feature (configurable constant WINDOW_SIZE).
- Warmup: first 100 keystrokes used to build baseline (WARMUP_THRESHOLD). During warmup, score is 0 with explanation "insufficient data for analysis".
- Baseline statistics via EWMA (EWMA_ALPHA): mean and variance per feature.

### Scoring model

- Compute z-score deviations for dwell and flight means w.r.t. EWMA baselines.
- Normalize each component into [0–1] and weight:
	- Dwell anomaly: 0.3
	- Flight anomaly: 0.3
	- Backspace rate elevation: 0.2
	- Paste detection flag: 0.2
- Explanations reflect which components contributed (e.g., "unusual inter-key timing", "rapid input detected").

### Public API (Java)

- start()/stop(): begin/end monitoring; clears buffers on stop.
- onKeyEvent(boolean isKeyDown, int keyCode, float pressure): feed events.
- getResult(): returns Agent.AgentResult(score, explanations, timestamp). Returns 0 with "insufficient data for analysis" during warmup or with very few keystrokes.
- resetBaseline(): clear baseline and buffers; re-enters warmup.
- isActive()/getName(): status helpers.
- simulateTyping(String pattern): test utility used by TypingAgentTest.

### Tuning knobs

- WINDOW_SIZE (default 50): larger smooths noise; smaller reacts faster.
- WARMUP_THRESHOLD (default 100): lower for tests; higher for production stability.
- EWMA_ALPHA (default 0.1): higher reacts faster; lower is smoother.
- Paste detection threshold (currently <10 ms flight): raise/lower depending on device/OS clock granularity.

### Edge cases

- No data / warmup: explanations include "insufficient data for analysis"; score=0.
- Baseline variance near zero: components are skipped to avoid divide-by-zero; overall score remains low.
- Keycode mapping: uses key codes only, never stores text content.

### Integration

- FusionEngine consumes the TypingAgent score via `fuseScores(touch, typing, usage)` and produces a final risk level with combined explanations.
- Logger records agent and fusion outputs (CSV/JSON-like lines) without sensitive content.

### How to run the demo test (Windows cmd)

```bat
cd C:\Users\malla\FraudGuard
javac -d . app\TypingAgentTest.java
java -cp . app.TypingAgentTest
```

Expected output includes a typing score (near 0 for normal) and fusion result JSON; a `typing_agent_test.log` file is written.

### Future improvements

- Pressure/force and hold variability features (when available on device).
- N-gram rhythm stability metrics (digraph/trigraph timing profiles).
- Adaptive thresholds per user/session and device type.

