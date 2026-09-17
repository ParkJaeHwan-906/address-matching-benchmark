.PHONY: build test es-up es-down all tiny small medium big large

BENCH := build/install/address-bench/bin/address-bench

build:
	./gradlew installDist
test:
	./gradlew test
es-up:
	docker compose up -d --wait elasticsearch
es-down:
	docker compose down
# 모든 시나리오(100 · 1만 · 10만 · 100만 · 1천만)를 순서대로 실행한다
all: build
	scripts/run-all.sh
tiny: build
	$(BENCH) generate --size 100 --queries 100 --output data/generated/tiny
	$(BENCH) index --dataset data/generated/tiny/addresses.ndjson --recreate
	$(BENCH) compare --dataset data/generated/tiny --output results/tiny-comparison-100.json
small: build
	$(BENCH) generate --size 10000 --queries 1000 --output data/generated/small
	$(BENCH) index --dataset data/generated/small/addresses.ndjson --recreate
	$(BENCH) compare --dataset data/generated/small --output results/small-comparison-100.json --query-limit 100
	$(BENCH) compare --dataset data/generated/small --output results/small-es-1000.json --skip-lcs
medium: build
	$(BENCH) generate --size 100000 --queries 1000 --output data/generated/medium
	$(BENCH) index --dataset data/generated/medium/addresses.ndjson --recreate
	$(BENCH) compare --dataset data/generated/medium --output results/medium-comparison-100.json --query-limit 100
	$(BENCH) compare --dataset data/generated/medium --output results/medium-es-1000.json --skip-lcs
big: build
	$(BENCH) generate --size 1000000 --queries 1000 --output data/generated/big
	$(BENCH) index --dataset data/generated/big/addresses.ndjson --recreate
	$(BENCH) compare --dataset data/generated/big --output results/big-comparison-100.json --query-limit 100
	$(BENCH) compare --dataset data/generated/big --output results/big-es-1000.json --skip-lcs
large: build
	$(BENCH) generate --size 10000000 --queries 1000 --output data/generated/large
	$(BENCH) index --dataset data/generated/large/addresses.ndjson --recreate
	$(BENCH) compare --dataset data/generated/large --output results/large-es-1000.json --skip-lcs
	$(BENCH) compare --dataset data/generated/large --output results/large-comparison-100.json --query-limit 100
