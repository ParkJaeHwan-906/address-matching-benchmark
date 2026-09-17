from dataclasses import dataclass
from heapq import heappush, heapreplace
from time import perf_counter_ns

from .normalize import normalize


def lcs_length(left: str, right: str) -> int:
    if len(left) > len(right):
        left, right = right, left
    previous = [0] * (len(left) + 1)
    for right_char in right:
        current = [0]
        for index, left_char in enumerate(left, 1):
            current.append(previous[index - 1] + 1 if left_char == right_char else max(previous[index], current[-1]))
        previous = current
    return previous[-1]


def similarity(left: str, right: str) -> float:
    left, right = normalize(left), normalize(right)
    denominator = max(len(left), len(right))
    return lcs_length(left, right) / denominator if denominator else 1.0


@dataclass(frozen=True)
class Match:
    address_id: int
    alias: str
    score: float


class ExhaustiveLcsMatcher:
    """Intentionally simple O(N * query_length * alias_length) baseline."""

    def __init__(self, records: list[tuple[int, str]]):
        self.records = records

    def search(self, query: str, top_k: int = 5) -> tuple[list[Match], int]:
        started = perf_counter_ns()
        heap: list[tuple[float, int, str]] = []
        for address_id, alias in self.records:
            item = (similarity(query, alias), address_id, alias)
            if len(heap) < top_k:
                heappush(heap, item)
            elif item > heap[0]:
                heapreplace(heap, item)
        matches = [Match(address_id, alias, score) for score, address_id, alias in sorted(heap, reverse=True)]
        return matches, perf_counter_ns() - started

