import argparse
import json
import os
from pathlib import Path

from .benchmark import load_queries, run, write_report
from .elasticsearch import ElasticsearchMatcher
from .generator import generate
from .lcs import ExhaustiveLcsMatcher


def load_records(path: Path) -> list[tuple[int, str]]:
    with path.open(encoding="utf-8") as source:
        return [(record["id"], record["alias"]) for record in map(json.loads, source)]


def main() -> None:
    parser = argparse.ArgumentParser(description="LCS vs Elasticsearch n-gram address matching benchmark")
    sub = parser.add_subparsers(dest="command", required=True)
    generator = sub.add_parser("generate")
    generator.add_argument("--size", type=int, required=True)
    generator.add_argument("--queries", type=int, default=1000)
    generator.add_argument("--output", type=Path, required=True)
    generator.add_argument("--seed", type=int, default=int(os.getenv("BENCHMARK_SEED", "20260917")))
    indexer = sub.add_parser("index")
    indexer.add_argument("--dataset", type=Path, required=True)
    indexer.add_argument("--recreate", action="store_true")
    compare = sub.add_parser("compare")
    compare.add_argument("--dataset", type=Path, required=True)
    compare.add_argument("--output", type=Path, required=True)
    compare.add_argument("--query-limit", type=int)
    compare.add_argument("--skip-lcs", action="store_true")
    args = parser.parse_args()
    es = ElasticsearchMatcher(os.getenv("ELASTICSEARCH_URL", "http://localhost:9200"), os.getenv("ELASTICSEARCH_INDEX", "address-mapping"))
    if args.command == "generate":
        generate(args.size, args.queries, args.output, args.seed)
    elif args.command == "index":
        if args.recreate:
            es.recreate_index()
        es.bulk_index(args.dataset)
    else:
        queries = load_queries(args.dataset / "queries.ndjson", args.query_limit)
        results = {"elasticsearch_ngram": run(es, queries)}
        if not args.skip_lcs:
            results["exhaustive_lcs"] = run(ExhaustiveLcsMatcher(load_records(args.dataset / "addresses.ndjson")), queries)
        write_report(args.output, args.dataset, results)


if __name__ == "__main__":
    main()

