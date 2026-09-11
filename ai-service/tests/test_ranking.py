import numpy as np

from app.ranking import cosine_similarities


def test_identical_vector_scores_one():
    q = np.array([1.0, 2.0, 3.0])
    sims = cosine_similarities(q, np.array([[1.0, 2.0, 3.0], [2.0, 4.0, 6.0]]))
    assert np.allclose(sims, [1.0, 1.0])


def test_orthogonal_and_opposite():
    q = np.array([1.0, 0.0])
    sims = cosine_similarities(q, np.array([[0.0, 1.0], [-1.0, 0.0]]))
    assert np.allclose(sims, [0.0, -1.0])


def test_zero_vector_does_not_produce_nan():
    q = np.array([1.0, 0.0])
    sims = cosine_similarities(q, np.array([[0.0, 0.0]]))
    assert sims[0] == 0.0
