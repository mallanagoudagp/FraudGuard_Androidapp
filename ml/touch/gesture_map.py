"""
Gesture mapping utilities for TouchAgent datasets.

This module centralizes the mapping between human-readable gesture labels
and their numeric encoding used by ML models.

Export/Import format (JSON):
{
  "TAP": 0.0,
  "SWIPE": 1.0
}

Provided functions:
- get_default_map() -> Dict[str, float]
- load_map(path) -> Dict[str, float]
- save_map(path, mapping) -> None
- to_numeric(gesture, mapping, default=None) -> float
- from_numeric(value, mapping, tol=1e-6) -> str
- ensure_supported(gestures, mapping) -> set[str] of unknowns
"""
from __future__ import annotations

import json
from typing import Dict, Iterable, Optional

# Default binary mapping; extend if you add more gesture types.
DEFAULT_MAP: Dict[str, float] = {
    "TAP": 0.0,
    "SWIPE": 1.0,
}


def get_default_map() -> Dict[str, float]:
    """Return a copy of the default gesture->numeric mapping."""
    return dict(DEFAULT_MAP)


def load_map(path: str) -> Dict[str, float]:
    """Load a gesture mapping from a JSON file.

    The JSON must be an object of string keys (labels) to numeric values.
    """
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError("Gesture map JSON must be an object {label: value, ...}")
    mapping: Dict[str, float] = {}
    for k, v in data.items():
        if not isinstance(k, str):
            raise ValueError("Gesture map keys must be strings")
        try:
            mapping[k] = float(v)
        except Exception as e:
            raise ValueError(f"Gesture map value for '{k}' must be numeric") from e
    if not mapping:
        raise ValueError("Gesture map is empty")
    return mapping


def save_map(path: str, mapping: Dict[str, float]) -> None:
    """Save the gesture mapping to a JSON file."""
    with open(path, "w", encoding="utf-8") as f:
        json.dump(mapping, f, indent=2, sort_keys=True)


def to_numeric(gesture: str, mapping: Dict[str, float], default: Optional[float] = None) -> float:
    """Map a gesture label to its numeric value.

    If the label is unknown and default is None, raises KeyError.
    """
    if gesture in mapping:
        return float(mapping[gesture])
    if default is not None:
        return float(default)
    raise KeyError(f"Unknown gesture label: {gesture}")


def from_numeric(value: float, mapping: Dict[str, float], tol: float = 1e-6) -> str:
    """Inverse mapping: numeric value to the closest matching label within tol.

    Raises KeyError if no label matches within tolerance.
    """
    for k, v in mapping.items():
        if abs(float(v) - float(value)) <= tol:
            return k
    raise KeyError(f"No label found for numeric value {value}")


def ensure_supported(gestures: Iterable[str], mapping: Dict[str, float]) -> set[str]:
    """Return set of labels from gestures that are not present in mapping."""
    mkeys = set(mapping.keys())
    return {g for g in gestures if g not in mkeys}
