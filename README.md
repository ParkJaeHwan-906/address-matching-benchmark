# Korean Address Matching Benchmark

OCR이 추출한 비표준 주소 문자열을 표준 주소에 연결하는 후처리 단계에서 **LCS 전수 탐색**과 **Elasticsearch n-gram 검색**을 비교하는 재현 가능한 성능 개선 프로젝트입니다. OCR 모델 자체는 범위에 포함하지 않습니다.

## 비교 시나리오

| 시나리오 | 매핑 테이블 | LCS | Elasticsearch |
|---|---:|---|---|
| Tiny | 100건 | 모든 후보와 LCS 계산 | 2~4-gram 역색인 검색 |
| Small | 10,000건 | 모든 후보와 LCS 계산 | 2~4-gram 역색인 검색 |
| Large | 10,000,000건 | 동일한 전수 탐색 기준선 | 동일한 역색인 검색 |

동일한 합성 주소와 오류 질의로 Top-1/Top-5 accuracy, MRR, p50/p95/p99 latency, QPS를 측정합니다. 실제 고객 주소나 OCR 원문은 저장하지 않습니다.

## 빠른 시작

Python 3.12+, Docker, Docker Compose가 필요합니다.

```bash
python -m venv .venv
./.venv/Scripts/activate
python -m pip install -e ".[dev]"
python -m pytest
docker compose up -d --wait elasticsearch
```

### 100건

```bash
address-bench generate --size 100 --queries 100 --output data/generated/tiny
address-bench index --dataset data/generated/tiny/addresses.ndjson --recreate
address-bench compare --dataset data/generated/tiny --output results/tiny-comparison-100.json
```

### 1만 건

```bash
address-bench generate --size 10000 --queries 1000 --output data/generated/small
address-bench index --dataset data/generated/small/addresses.ndjson --recreate
address-bench compare --dataset data/generated/small --output results/small.json
```

### 1천만 건

```bash
address-bench generate --size 10000000 --queries 1000 --output data/generated/large
address-bench index --dataset data/generated/large/addresses.ndjson --recreate
address-bench compare --dataset data/generated/large --output results/large.json
```

10M 행 LCS 전수 탐색은 의도적으로 비효율적인 기준선입니다. 먼저 `--query-limit 10`으로 실행 가능성을 확인하세요. Elasticsearch만 측정할 때는 `--skip-lcs`를 사용합니다. 완주하지 못한 LCS 결과를 외삽값과 혼합하지 않습니다.

자세한 평가 기준은 [실험 설계](docs/EXPERIMENT.md)를 참고하세요.

실측 수치와 해석은 [벤치마크 결과](docs/BENCHMARK_RESULTS.md), 원본 JSON은 [`results/`](results/)에서 확인할 수 있습니다.

## 배경

실무에서 아파트·빌라명, 약칭, OCR 오탈자로 주소 API 검색이 실패하는 문제를 LCS 문자열 매칭과 전용 주소 매핑 DB로 보정했습니다. 이 저장소는 그 경험을 개인정보 없이 재구성하고, 데이터가 1만 건에서 1천만 건으로 커질 때 선형 전수 탐색을 역색인 기반 검색으로 전환해야 하는 근거를 실측하기 위한 프로젝트입니다.

## 라이선스

개인 포트폴리오용으로 공개한 저장소입니다. 모든 주소는 seed로 생성한 합성 데이터이며, 실제 고객 주소·OCR 원문·회사 코드는 포함하지 않습니다.
