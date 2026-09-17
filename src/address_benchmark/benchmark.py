import json
import math
import platform
import statistics
from dataclasses import asdict, dataclass
from pathlib import Path
from time import time


@dataclass
class Metrics:
    queries: int
    top1_accuracy: float
    top5_accuracy: float
    mrr: float
    latency_ms_p50: float
    latency_ms_p95: float
    latency_ms_p99: float
    throughput_qps: float


def percentile(values: list[float], percent: float) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(percent / 100 * len(ordered)) - 1)]


def run(matcher, queries: list[dict], warmup: int = 10) -> Metrics:
    if not queries:
        raise ValueError("query set must not be empty")
    for item in queries[:warmup]:
        matcher.search(item["query"])
    latencies, top1, top5, reciprocal_ranks = [], 0, 0, []
    wall_started = time()
    for item in queries:
        matches, elapsed_ns = matcher.search(item["query"])
        ids = [match.address_id for match in matches]
        latencies.append(elapsed_ns / 1_000_000)
        top1 += bool(ids and ids[0] == item["expected_id"])
        top5 += item["expected_id"] in ids
        reciprocal_ranks.append(1 / (ids.index(item["expected_id"]) + 1) if item["expected_id"] in ids else 0)
    wall = time() - wall_started
    count = len(queries)
    return Metrics(count, top1 / count, top5 / count, statistics.mean(reciprocal_ranks), percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 99), count / wall)


def load_queries(path: Path, limit: int | None = None) -> list[dict]:
    with path.open(encoding="utf-8") as source:
        result = [json.loads(line) for line in source]
    return result[:limit]


def write_report(path: Path, dataset: Path, results: dict) -> None:
    metadata = json.loads((dataset / "metadata.json").read_text(encoding="utf-8"))
    report = {"dataset": metadata, "environment": {"python": platform.python_version(), "platform": platform.platform()}, "results": {key: asdict(value) for key, value in results.items()}}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

