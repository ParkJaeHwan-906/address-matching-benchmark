import json

from address_benchmark.generator import generate


def test_generator_is_reproducible(tmp_path):
    first, second = tmp_path / "first", tmp_path / "second"
    generate(100, 10, first, 42)
    generate(100, 10, second, 42)
    assert (first / "addresses.ndjson").read_bytes() == (second / "addresses.ndjson").read_bytes()
    assert json.loads((first / "metadata.json").read_text(encoding="utf-8"))["size"] == 100

