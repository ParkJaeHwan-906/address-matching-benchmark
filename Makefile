.PHONY: install test es-up es-down tiny-data small-data large-data index-tiny index-small index-large bench-tiny bench-small bench-large
install:
	python -m pip install -e ".[dev]"
test:
	python -m pytest
es-up:
	docker compose up -d --wait elasticsearch
es-down:
	docker compose down
tiny-data:
	address-bench generate --size 100 --queries 100 --output data/generated/tiny
small-data:
	address-bench generate --size 10000 --queries 1000 --output data/generated/small
large-data:
	address-bench generate --size 10000000 --queries 1000 --output data/generated/large
index-tiny:
	address-bench index --dataset data/generated/tiny/addresses.ndjson --recreate
index-small:
	address-bench index --dataset data/generated/small/addresses.ndjson --recreate
index-large:
	address-bench index --dataset data/generated/large/addresses.ndjson --recreate
bench-tiny:
	address-bench compare --dataset data/generated/tiny --output results/tiny-comparison-100.json
bench-small:
	address-bench compare --dataset data/generated/small --output results/small.json
bench-large:
	address-bench compare --dataset data/generated/large --output results/large.json
