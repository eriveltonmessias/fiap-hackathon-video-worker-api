# Fluxo ponta a ponta dos tres projetos

A collection `FIAP X - Fluxo Ponta a Ponta.postman_collection.json` executa:

```text
customer-auth-api -> video-manager-api -> Kafka -> video-worker-api
                  <- video-manager-api <- Kafka <-
```

Ela cadastra um cliente, autentica, envia um video ao manager, aguarda o worker
produzir o ZIP, confirma o status final no manager e baixa o resultado.

## 1. Estrutura esperada

Os repositorios devem estar lado a lado:

```text
hackton-fiap/
  customer-auth-api/
  video-manager-api/
  video-worker-api/
```

## 2. Subir os tres projetos e a infraestrutura

Dentro de `video-worker-api`:

```bash
docker compose -f docker-compose.full.yml up --build -d
docker compose -f docker-compose.full.yml ps
```

O Compose sobe os tres projetos e uma unica instancia compartilhada de Kafka e
MinIO, alem de PostgreSQL para auth e manager e MongoDB para o worker.

| Componente | Endereco local |
| --- | --- |
| Customer Auth API | `http://localhost:8081` |
| Video Manager API | `http://localhost:8082` |
| Video Worker API | `http://localhost:8083` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| Kafka | `localhost:9092` |
| PostgreSQL Auth | `localhost:5433` |
| PostgreSQL Manager | `localhost:5434` |
| MongoDB Worker | `localhost:27017` |

Espere os health checks:

```bash
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health/readiness
curl --fail http://localhost:8083/actuator/health/readiness
```

Para acompanhar a inicializacao:

```bash
docker compose -f docker-compose.full.yml logs -f \
  customer-auth-api video-manager-api video-worker-api
```

## 3. Executar a collection

1. Importe `postman/FIAP X - Fluxo Ponta a Ponta.postman_collection.json`.
2. Abra `02 - Processamento / 01 - Upload do video`.
3. Em **Body > form-data**, selecione `postman/assets/sample-video.mp4` caso o
   Postman nao preserve o caminho relativo.
4. Execute a collection inteira no Collection Runner, mantendo a ordem.

As fases sao:

1. saude dos tres projetos;
2. cadastro, login e validacao do cliente;
3. upload, polling do processamento e download do ZIP;
4. consultas finais e metricas.

O polling repete automaticamente no Collection Runner por ate 60 segundos. No
envio manual, repita `02 - Aguardar processamento` enquanto o status ainda nao
for `PROCESSED`.

## 4. Encerrar

Preserve os dados:

```bash
docker compose -f docker-compose.full.yml down
```

Remova tambem os volumes:

```bash
docker compose -f docker-compose.full.yml down --volumes
```
