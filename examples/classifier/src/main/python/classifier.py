from types import SimpleNamespace


def predict(features):
    total = sum(features)
    label = "positive" if total >= 0 else "negative"
    confidence = min(1.0, abs(total))
    return SimpleNamespace(label=label, confidence=confidence)
