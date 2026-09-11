from app.embeddings import batch_by_chars


def test_packs_texts_up_to_char_budget_in_order():
    texts = ["a" * 40, "b" * 40, "c" * 40, "d" * 10]
    assert batch_by_chars(texts, max_chars=100) == [[0, 1], [2, 3]]


def test_respects_item_limit():
    assert batch_by_chars(["x"] * 5, max_items=2) == [[0, 1], [2, 3], [4]]


def test_oversized_text_gets_its_own_batch():
    assert batch_by_chars(["a" * 500, "b"], max_chars=100) == [[0], [1]]


def test_empty_input():
    assert batch_by_chars([]) == []
