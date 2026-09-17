import json
import random
from pathlib import Path

SIDOS = ["서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도"]
DISTRICTS = ["중구", "서구", "동구", "남구", "북구", "강남구", "송파구", "분당구", "수지구", "일산동구", "해운대구", "유성구"]
ROADS = ["중앙로", "테헤란로", "한밭대로", "충무로", "세종대로", "올림픽로", "송파대로", "판교역로", "광교중앙로", "해운대로"]
BUILDINGS = ["행복아파트", "푸른마을", "센트럴빌", "한빛타워", "미래빌라", "더샵", "아이파크", "래미안", "힐스테이트", "자이"]


def canonical_address(index: int) -> str:
    # The lot/building pair is unique for every row. Without this invariant,
    # a large synthetic corpus can assign the same address to different IDs,
    # making Top-k accuracy mathematically ambiguous rather than measuring search.
    lot = index // 120 + 1
    building = index % 120 + 1
    return f"{SIDOS[index % len(SIDOS)]} {DISTRICTS[(index // 16) % len(DISTRICTS)]} {ROADS[(index // 192) % len(ROADS)]} {lot}-{building} {BUILDINGS[(index // 1920) % len(BUILDINGS)]} {building}동"


def noisy_alias(address: str, rng: random.Random) -> str:
    result = address
    for old, new in [("특별자치도", "도"), ("특별시", ""), ("광역시", ""), ("아파트", "APT"), ("빌라", "BILA"), (" ", "")]:
        if rng.random() < 0.22:
            result = result.replace(old, new)
    if len(result) > 5 and rng.random() < 0.55:
        position = rng.randrange(len(result))
        result = result[:position] + result[position + 1 :]
    if len(result) > 5 and rng.random() < 0.35:
        position = rng.randrange(len(result))
        result = result[:position] + rng.choice("가나다라마바사아자0123456789") + result[position + 1 :]
    return result


def generate(size: int, query_count: int, output: Path, seed: int) -> None:
    output.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    query_ids = set(rng.sample(range(size), min(query_count, size)))
    with (output / "addresses.ndjson").open("w", encoding="utf-8") as addresses, (output / "queries.ndjson").open("w", encoding="utf-8") as queries:
        for index in range(size):
            canonical = canonical_address(index)
            alias = noisy_alias(canonical, rng)
            addresses.write(json.dumps({"id": index, "canonical": canonical, "alias": alias}, ensure_ascii=False) + "\n")
            if index in query_ids:
                queries.write(json.dumps({"query": noisy_alias(alias, rng), "expected_id": index}, ensure_ascii=False) + "\n")
    metadata = {"size": size, "query_count": len(query_ids), "seed": seed, "synthetic": True}
    (output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
