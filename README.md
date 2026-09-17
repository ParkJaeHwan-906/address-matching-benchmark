# Korean Address Matching Benchmark

OCR이 추출한 비표준 주소 문자열을 표준 주소에 연결하는 후처리 단계에서 **LCS 전수 탐색**과 **Elasticsearch n-gram 검색**을 비교하는 재현 가능한 성능 개선 프로젝트입니다. Java 21로 작성했고 OCR 모델 자체는 범위에 포함하지 않습니다.

## 비교 시나리오

| 시나리오 | 매핑 테이블 | LCS | Elasticsearch |
|---|---:|---|---|
| Tiny | 100건 | 모든 후보와 LCS 계산 | 2~4-gram 역색인 검색 |
| Small | 10,000건 | 모든 후보와 LCS 계산 | 2~4-gram 역색인 검색 |
| Medium | 100,000건 | 모든 후보와 LCS 계산 | 2~4-gram 역색인 검색 |
| Big | 1,000,000건 | 모든 후보와 LCS 계산 | 2~4-gram 역색인 검색 |
| Large | 10,000,000건 | 동일한 전수 탐색 기준선 | 동일한 역색인 검색 |

동일한 합성 주소와 오류 질의로 Top-1/Top-5 accuracy, MRR, p50/p95/p99 latency, QPS를 측정합니다. 실제 고객 주소나 OCR 원문은 저장하지 않습니다.

## 구성

```
src/main/java/com/parkjaehwan/addressbench/
├── Cli.java                    generate · index · compare 서브커맨드 (picocli)
├── Generator.java              seed 기반 합성 주소·오류 질의 생성기
├── Normalizer.java             NFKC → 소문자 → 숫자·영문·한글만 남김
├── Lcs.java                    두 행 DP로 LCS 길이·유사도 계산
├── ExhaustiveLcsMatcher.java   모든 후보와 LCS 유사도를 계산하는 기준선 (Top-k 힙)
├── ElasticsearchMatcher.java   2~4-gram 인덱스 생성 · bulk 색인 · match 검색 (java.net.http)
└── Benchmark.java              Top-1 · Top-5 · MRR · p50/p95/p99 · QPS 측정과 JSON 리포트
```

의존성은 picocli와 Jackson 둘뿐이며 Elasticsearch 클라이언트 라이브러리 없이 HTTP로 직접 호출합니다.

## 빠른 시작

JDK 21, Docker, Docker Compose가 필요합니다. Gradle은 wrapper가 내려받습니다.

```bash
./gradlew test
./gradlew installDist          # build/install/address-bench/bin/address-bench
docker compose up -d --wait elasticsearch
```

### 100건

```bash
B=build/install/address-bench/bin/address-bench
$B generate --size 100 --queries 100 --output data/generated/tiny
$B index --dataset data/generated/tiny/addresses.ndjson --recreate
$B compare --dataset data/generated/tiny --output results/tiny-comparison-100.json
```

### 1만 건 · 10만 건 · 100만 건

```bash
$B generate --size 10000 --queries 1000 --output data/generated/small
$B index --dataset data/generated/small/addresses.ndjson --recreate
$B compare --dataset data/generated/small --output results/small-comparison-100.json --query-limit 100
$B compare --dataset data/generated/small --output results/small-es-1000.json --skip-lcs
```

`--size 100000 … data/generated/medium`, `--size 1000000 … data/generated/big` 도 같은 순서입니다.

### 1천만 건

```bash
$B generate --size 10000000 --queries 1000 --output data/generated/large
$B index --dataset data/generated/large/addresses.ndjson --recreate
$B compare --dataset data/generated/large --output results/large-es-1000.json --skip-lcs
$B compare --dataset data/generated/large --output results/large-comparison-100.json --query-limit 100
```

`scripts/run-all.sh`(또는 `make all`)는 위 다섯 시나리오를 순서대로 실행합니다. 인덱스 이름이 같아 시나리오마다 다시 만들기 때문에 순서대로 돌려야 합니다. 1천만 건 LCS는 alias 1천만 개를 메모리에 올리므로 `-Xmx6g`(기본 설정)가 필요합니다.

옵션은 `--query-limit N`(앞에서 N개 질의만), `--warmup N`(측정 전 warm-up, 기본 10), `--skip-lcs`(Elasticsearch만)이며, 환경변수 `ELASTICSEARCH_URL` · `ELASTICSEARCH_INDEX` · `BENCHMARK_SEED`로 접속 정보와 seed를 바꿀 수 있습니다(`.env.example`).

자세한 평가 기준은 [실험 설계](docs/EXPERIMENT.md), 실측 수치와 해석은 [벤치마크 결과](docs/BENCHMARK_RESULTS.md), 원본 JSON은 [`results/`](results/)에서 확인할 수 있습니다. Python으로 작성했던 첫 버전의 결과는 [`results/archive-python/`](results/archive-python/)에 남겨 두었습니다.

## 배경

실무에서 아파트·빌라명, 약칭, OCR 오탈자로 주소 API 검색이 실패하는 문제를 LCS 문자열 매칭과 전용 주소 매핑 DB로 보정했습니다. 이 저장소는 그 경험을 개인정보 없이 재구성하고, 데이터가 커질 때 선형 전수 탐색을 역색인 기반 검색으로 전환해야 하는 지점을 실측하기 위한 프로젝트입니다.

## 라이선스

개인 포트폴리오용으로 공개한 저장소입니다. 모든 주소는 seed로 생성한 합성 데이터이며, 실제 고객 주소·OCR 원문·회사 코드는 포함하지 않습니다.
