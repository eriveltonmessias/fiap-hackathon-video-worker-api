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
- outbox persistido com o job e publicacao confirmada pelo Kafka;
- retry limitado para falhas transitorias e DLQ sanitizada para mensagens irrecuperaveis;
- metricas Prometheus, logs ECS correlacionados e probes das dependencias;
- separacao entre dominio, aplicacao e infraestrutura.

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

Resultados sao publicados com chave Kafka igual ao `videoId`:

- `VideoProcessed` no topico `video.processing.completed`;
- `VideoProcessingFailed` no topico `video.processing.failed`;
- `VideoProcessingRequestDeadLettered` no topico `video.processing.requested.dlq`.

O mesmo `eventId` pode ser publicado novamente se o ACK do Kafka ocorrer antes
da confirmacao no MongoDB. Consumidores devem tratar esse identificador de forma
idempotente.

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
| `KAFKA_PROCESSING_MAX_ATTEMPTS` | `3` |
| `KAFKA_PROCESSING_RETRY_INTERVAL` | `1s` |
| `KAFKA_DLQ_PUBLISH_TIMEOUT` | `10s` |
| `KAFKA_HEALTH_TIMEOUT` | `3s` |
| `OUTBOX_SCHEDULING_ENABLED` | `true` |
| `OUTBOX_INITIAL_DELAY` | `5s` |
| `OUTBOX_FIXED_DELAY` | `5s` |
| `OUTBOX_BATCH_SIZE` | `100` |
| `OUTBOX_PUBLISH_TIMEOUT` | `10s` |
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

## EKS

A liveness verifica apenas o estado interno da aplicacao. A readiness inclui
MongoDB, Kafka e os buckets MinIO, evitando reiniciar o pod por uma dependencia
temporariamente indisponivel.

```yaml
ports:
  - name: management
    containerPort: 8083
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: management
  periodSeconds: 10
  timeoutSeconds: 3
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: management
  periodSeconds: 5
  timeoutSeconds: 3
terminationGracePeriodSeconds: 45
```

No `SIGTERM`, o listener Kafka para de buscar novas mensagens e aguarda o
processamento corrente dentro de `SHUTDOWN_TIMEOUT`. Processos FFmpeg ainda
ativos sao encerrados ao fechar o contexto. O tempo de terminacao do pod deve
ser maior que `SHUTDOWN_TIMEOUT`.

O endpoint `/actuator/prometheus` expoe, entre outras, as metricas:

- `video_worker_jobs_total`;
- `video_worker_processing_duration_seconds`;
- `video_worker_frames_generated_total`;
- `video_worker_failures_total`;
- `video_worker_processing_retries_total`.

Os logs usam o formato ECS e recebem `videoId` e `eventId` via MDC durante o
consumo. Esses identificadores nao sao usados como tags de metricas.
