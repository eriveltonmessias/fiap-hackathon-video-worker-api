# Video Worker API

Worker assincrono da FIAP X responsavel por transformar videos em frames,
compactar o resultado e informar a conclusao ao `video-manager-api`.

## Estado atual

A primeira estrutura inclui:

- Java 21, Kotlin, Spring Boot e Gradle Wrapper;
- configuracao base para MongoDB, Kafka e MinIO;
- Actuator com liveness, readiness e Prometheus;
- shutdown gracioso;
- configuracoes externas por variaveis de ambiente;
- agregado `VideoProcessingJob` sem dependencias de framework;
- ciclo interno do processamento com invariantes e estados terminais;
- UUID direto para identificadores e VOs somente para valores que exigem validacao;
- persistencia MongoDB com indices unicos por video e evento de entrada;
- download e upload por streaming, sem materializar arquivos temporarios no adapter;
- inicializacao idempotente dos buckets de entrada e saida;
- separacao entre dominio, aplicacao e infraestrutura.

O processamento e os adapters serao adicionados em cortes pequenos, com uma
branch e validacao independente para cada etapa.

## Arquitetura

```text
src/main/kotlin/com/fiap/hackathon/videoworkerapi/
  domain/processing/          regras e modelos do job
  application/processing/     orquestracao e portas
  infrastructure/processing/  Kafka, MongoDB, MinIO e FFmpeg
```

O dominio nao dependera de Spring. A aplicacao coordenara o fluxo, e a
infraestrutura implementara as integracoes externas.

O job controla o ciclo:

```text
RECEIVED -> PROCESSING -> GENERATING_FRAMES -> COMPRESSING
         -> UPLOADING_RESULT -> COMPLETED
```

Qualquer estado nao terminal pode finalizar em `FAILED`. `COMPLETED` e `FAILED`
nao aceitam novas transicoes.

## Requisitos

- Java 21
- Docker para os testes de integracao das proximas etapas

Nao e necessario instalar Gradle globalmente.

## Build e testes

```bash
./gradlew test
./gradlew build
```

## Executar

Inicie MongoDB e MinIO localmente:

```bash
docker compose up -d mongo-worker minio
```

Com Kafka tambem disponivel na porta local padrao:

```bash
./gradlew bootRun
```

A aplicacao usa a porta `8083`. Neste primeiro corte, ela ainda nao possui
consumer Kafka e inicia mesmo quando as dependencias locais estao indisponiveis.

```bash
curl http://localhost:8083/actuator/health/liveness
curl http://localhost:8083/actuator/health/readiness
```

## Variaveis

| Variavel | Padrao |
| --- | --- |
| `SERVER_PORT` | `8083` |
| `MANAGEMENT_SERVER_PORT` | valor de `SERVER_PORT` |
| `MONGODB_URI` | `mongodb://localhost:27017/video_worker_db` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `KAFKA_PROCESSING_REQUESTS_GROUP_ID` | `video-worker-processing-requests` |
| `MINIO_ENDPOINT` | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | `fiapx` |
| `MINIO_SECRET_KEY` | `fiapx12345` |
| `MINIO_INPUT_BUCKET` | `fiapx-videos-input` |
| `MINIO_OUTPUT_BUCKET` | `fiapx-videos-output` |
| `MINIO_INITIALIZE_BUCKETS` | `true` |
| `SHUTDOWN_TIMEOUT` | `30s` |

Os valores sao somente para desenvolvimento local. No EKS, credenciais devem
vir de Kubernetes Secrets ou de um gerenciador externo.
