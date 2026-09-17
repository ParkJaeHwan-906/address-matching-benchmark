from address_benchmark.lcs import lcs_length, similarity


def test_lcs_length():
    assert lcs_length("강남래미안", "강남구래미안") == 5


def test_similarity_ignores_spaces_and_punctuation():
    assert similarity("서울 강남구-래미안", "서울강남구래미안") == 1.0

