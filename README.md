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
- consumo validado e idempotente de `VideoProcessingRequested` pelo Kafka;
- extracao segura de frames JPEG com FFmpeg, timeout e limite de frames;
- compactacao ZIP nativa com todos os frames extraidos;
- pipeline completo com download, transicoes persistidas, upload e limpeza temporaria;
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
- Docker para os testes de integracao
- FFmpeg disponivel no `PATH`

Nao e necessario instalar Gradle globalmente.

## Build e testes

```bash
./gradlew test
./gradlew build
```

## Executar

Inicie MongoDB, MinIO e Kafka localmente:

```bash
docker compose up -d mongo-worker minio kafka
```

Execute o worker:

```bash
./gradlew bootRun
```

A aplicacao usa a porta `8083`. O consumer Kafka inicia por padrao e aguarda as
dependencias locais quando elas estiverem indisponiveis.

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
| `PROCESSING_TEMP_DIRECTORY` | diretorio temporario da JVM |
| `FFMPEG_EXECUTABLE` | `ffmpeg` |
| `FFMPEG_TIMEOUT` | `5m` |
| `FFMPEG_FRAMES_PER_SECOND` | `1` |
| `FFMPEG_MAX_FRAMES` | `10000` |
| `MINIO_ENDPOINT` | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | `fiapx` |
| `MINIO_SECRET_KEY` | `fiapx12345` |
| `MINIO_INPUT_BUCKET` | `fiapx-videos-input` |
| `MINIO_OUTPUT_BUCKET` | `fiapx-videos-output` |
| `MINIO_INITIALIZE_BUCKETS` | `true` |
| `SHUTDOWN_TIMEOUT` | `30s` |

Os valores sao somente para desenvolvimento local. No EKS, credenciais devem
vir de Kubernetes Secrets ou de um gerenciador externo. O diretorio temporario
pode apontar para um volume `emptyDir` com limite de armazenamento definido.
