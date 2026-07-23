# Fluxo local com Postman

A collection `Video Worker - Fluxo Completo.postman_collection.json` percorre o
processamento de ponta a ponta. Ela usa:

- Actuator para saude e metricas;
- API S3 do MinIO para upload do video e download do ZIP;
- Kafka REST Proxy para publicar e consumir os eventos;
- o worker real para MongoDB, FFmpeg, compactacao e outbox.

O Kafka REST Proxy existe apenas no profile local `flow`. Ele nao faz parte do
deployment do EKS.

## 1. Pre-requisitos

- Docker Desktop ou Docker Engine com Compose;
- Postman Desktop;
- portas `8083`, `8084`, `9000`, `9001`, `9092` e `27017` livres.

## 2. Subir toda a infraestrutura

Na raiz do projeto:

```bash
docker compose --profile flow up --build -d
```

Esse comando inicia:

| Servico | Porta | Finalidade |
| --- | --- | --- |
| Video Worker | `8083` | worker e Actuator |
| Kafka REST Proxy | `8084` | acesso HTTP local ao Kafka |
| MinIO API | `9000` | armazenamento dos videos e ZIPs |
| MinIO Console | `9001` | inspecao visual dos buckets |
| Kafka | `9092` | broker local |
| MongoDB | `27017` | persistencia dos jobs e outbox |

Acompanhe a inicializacao:

```bash
docker compose --profile flow ps
docker compose --profile flow logs -f video-worker
```

Espere a readiness responder com `UP`:

```bash
curl --fail http://localhost:8083/actuator/health/readiness
curl --fail http://localhost:8084/topics
curl --fail http://localhost:9000/minio/health/live
```

## 3. Importar e executar

1. Importe `postman/Video Worker - Fluxo Completo.postman_collection.json`.
2. Abra `01 - Sucesso / 01 - Upload do video`.
3. Em **Body > binary**, selecione `postman/assets/sample-video.mp4` caso o
   Postman nao preserve o caminho relativo importado.
4. Execute a collection no Collection Runner, na ordem original.

A collection gera UUIDs novos, envia o video, cria um consumidor temporario,
publica `VideoProcessingRequested`, aguarda `VideoProcessed`, baixa o ZIP e
repete o evento para validar a entrada idempotente. Depois executa um fluxo sem
objeto no MinIO e aguarda `VideoProcessingFailed`.

Os polls se repetem automaticamente no Collection Runner por ate 30 segundos.
Ao enviar requests manualmente, repita o request de poll enquanto a resposta
estiver vazia.

O download retorna bytes ZIP. No envio manual, use **Send and Download** para
gravar e abrir o arquivo.

## 4. Inspecionar os dados

- MinIO Console: `http://localhost:9001`
- usuario: `fiapx`
- senha: `fiapx12345`
- MongoDB: banco `video_worker_db`, collection `video_processing_jobs`
- metricas: `http://localhost:8083/actuator/prometheus`

O bucket de entrada e `fiapx-videos-input`; o de saida e
`fiapx-videos-output`.

## 5. Encerrar

Preserve os volumes:

```bash
docker compose --profile flow down
```

Remova tambem todos os dados locais:

```bash
docker compose --profile flow down --volumes
```
