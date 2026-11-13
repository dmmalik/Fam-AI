"""Map a prediction to low, medium, or high.

Disease severity follows lesion area: under 10% is low, 10% through 30% is
medium, and above 30% is high. Pests have no lesion to measure, so confidence
is used instead: under 0.60 is low, from 0.60 up to but not including 0.85 is
medium, and 0.85 and above is high.
"""

from __future__ import annotations


def severity_for(kind: str, lesion_ratio: float, confidence: float) -> str:
    if kind == "pest":
        score = float(confidence)
        if score < 0.60:
            return "low"
        if score < 0.85:
            return "medium"
        return "high"
    ratio = float(lesion_ratio)
    if ratio < 0.10:
        return "low"
    if ratio <= 0.30:
        return "medium"
    return "high"
