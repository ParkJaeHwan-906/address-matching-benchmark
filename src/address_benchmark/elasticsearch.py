import json
import urllib.error
import urllib.request
from pathlib import Path
from time import perf_counter_ns

from .lcs import Match

INDEX_SETTINGS = {
    "settings": {
        "index": {"max_ngram_diff": 2},
        "analysis": {
            "char_filter": {
                "address_cleanup": {
                    "type": "pattern_replace",
                    "pattern": "[^\\p{L}\\p{N}]",
                    "replacement": "",
                }
            },
            "tokenizer": {
                "address_ngram": {
                    "type": "ngram",
                    "min_gram": 2,
                    "max_gram": 4,
                    "token_chars": ["letter", "digit"],
                }
            },
            "analyzer": {
                "address_ngram_analyzer": {
                    "type": "custom",
                    "char_filter": ["address_cleanup"],
                    "tokenizer": "address_ngram",
                    "filter": ["lowercase"],
                }
            },
        },
    },
    "mappings": {
        "properties": {
            "id": {"type": "long"},
            "canonical": {"type": "keyword"},
            "alias": {
                "type": "text",
                "analyzer": "address_ngram_analyzer",
                "search_analyzer": "address_ngram_analyzer",
            },
        }
    },
}


class ElasticsearchMatcher:
    def __init__(self, base_url: str, index: str):
        self.base_url, self.index = base_url.rstrip("/"), index

    def _request(self, method: str, path: str, body: bytes | None = None, content_type: str = "application/json") -> dict:
        request = urllib.request.Request(self.base_url + path, data=body, method=method, headers={"Content-Type": content_type})
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = response.read()
            return json.loads(payload) if payload else {}

    def recreate_index(self) -> None:
        try:
            self._request("DELETE", f"/{self.index}")
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
        self._request("PUT", f"/{self.index}", json.dumps(INDEX_SETTINGS).encode())

    def bulk_index(self, dataset: Path, batch_size: int = 5000) -> None:
        batch: list[str] = []
        with dataset.open(encoding="utf-8") as source:
            for line in source:
                record = json.loads(line)
                batch.extend((json.dumps({"index": {"_index": self.index, "_id": record["id"]}}), json.dumps(record, ensure_ascii=False)))
                if len(batch) >= batch_size * 2:
                    self._bulk(batch)
                    batch.clear()
        if batch:
            self._bulk(batch)
        self._request("POST", f"/{self.index}/_refresh")

    def _bulk(self, lines: list[str]) -> None:
        result = self._request("POST", "/_bulk", ("\n".join(lines) + "\n").encode(), "application/x-ndjson")
        if result.get("errors"):
            raise RuntimeError("Elasticsearch bulk indexing returned errors")

    def search(self, query: str, top_k: int = 5) -> tuple[list[Match], int]:
        body = json.dumps({"size": top_k, "_source": ["id", "alias"], "query": {"match": {"alias": {"query": query, "minimum_should_match": "55%"}}}}, ensure_ascii=False).encode()
        started = perf_counter_ns()
        result = self._request("POST", f"/{self.index}/_search", body)
        elapsed = perf_counter_ns() - started
        matches = [Match(int(hit["_source"]["id"]), hit["_source"]["alias"], float(hit["_score"])) for hit in result["hits"]["hits"]]
        return matches, elapsed
