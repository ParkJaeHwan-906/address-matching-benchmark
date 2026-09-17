#!/usr/bin/env bash
# 모든 시나리오를 같은 절차로 실행한다. 인덱스는 시나리오마다 다시 만들므로 순서대로 돌려야 한다.
set -euo pipefail
cd "$(dirname "$0")/.."
B=build/install/address-bench/bin/address-bench
run() { echo; echo "### $(date +%H:%M:%S) $*"; "$@"; }

run $B generate --size 100 --queries 100 --output data/generated/tiny
run $B index --dataset data/generated/tiny/addresses.ndjson --recreate
run $B compare --dataset data/generated/tiny --output results/tiny-comparison-100.json

for spec in "small 10000" "medium 100000" "big 1000000"; do
  set -- $spec
  run $B generate --size "$2" --queries 1000 --output "data/generated/$1"
  run $B index --dataset "data/generated/$1/addresses.ndjson" --recreate
  run $B compare --dataset "data/generated/$1" --output "results/$1-comparison-100.json" --query-limit 100
  run $B compare --dataset "data/generated/$1" --output "results/$1-es-1000.json" --skip-lcs
done

run $B generate --size 10000000 --queries 1000 --output data/generated/large
run $B index --dataset data/generated/large/addresses.ndjson --recreate
run $B compare --dataset data/generated/large --output results/large-es-1000.json --skip-lcs
run $B compare --dataset data/generated/large --output results/large-comparison-100.json --query-limit 100
echo; echo "### $(date +%H:%M:%S) all done"
