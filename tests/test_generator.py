import json

from address_benchmark.generator import generate


def test_generator_is_reproducible(tmp_path):
    first, second = tmp_path / "first", tmp_path / "second"
    generate(100, 10, first, 42)
    generate(100, 10, second, 42)
    assert (first / "addresses.ndjson").read_bytes() == (second / "addresses.ndjson").read_bytes()
    assert json.loads((first / "metadata.json").read_text(encoding="utf-8"))["size"] == 100


def test_canonical_addresses_are_unique_at_scale():
    from address_benchmark.generator import canonical_address

    values = {canonical_address(index) for index in range(100_000)}
    assert len(values) == 100_000
