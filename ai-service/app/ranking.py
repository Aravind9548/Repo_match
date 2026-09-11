import numpy as np


def cosine_similarities(query: np.ndarray, docs: np.ndarray) -> np.ndarray:
    """Cosine similarity of one query vector against each row of `docs`."""
    q_norm = np.linalg.norm(query)
    d_norms = np.linalg.norm(docs, axis=1)
    denom = d_norms * q_norm
    denom[denom == 0] = 1.0  # zero vectors score 0 instead of NaN
    return (docs @ query) / denom
