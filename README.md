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
- separacao planejada entre dominio, aplicacao e infraestrutura.

O processamento e os adapters serao adicionados em cortes pequenos conforme
[docs/development-plan.md](docs/development-plan.md).

## Arquitetura

```text
src/main/kotlin/com/fiap/hackathon/videoworkerapi/
  domain/processing/          regras e modelos do job
  application/processing/     orquestracao e portas
  infrastructure/processing/  Kafka, MongoDB, MinIO e FFmpeg
```

O dominio nao dependera de Spring. A aplicacao coordenara o fluxo, e a
infraestrutura implementara as integracoes externas.

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

Com MongoDB, Kafka e MinIO disponiveis nas portas locais padrao:

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
| `SHUTDOWN_TIMEOUT` | `30s` |

Os valores sao somente para desenvolvimento local. No EKS, credenciais devem
vir de Kubernetes Secrets ou de um gerenciador externo.
