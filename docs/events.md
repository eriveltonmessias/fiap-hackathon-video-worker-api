# Contratos Kafka

Todos os eventos usam UUIDs em formato textual, datas ISO-8601 em UTC e a chave
Kafka igual ao `videoId`.

## VideoProcessingRequested

Topico consumido: `video.processing.requested`

```json
{
  "eventId": "64019a64-8dde-4ef5-b42d-43e33a74db47",
  "eventType": "VideoProcessingRequested",
  "occurredAt": "2026-07-23T12:00:00Z",
  "videoId": "db707a51-7216-4a87-bb55-807a10537514",
  "customerId": "bb3de92f-14b9-4b26-a115-b1e65a25b406",
  "originalFilename": "lesson.mp4",
  "inputObjectKey": "customers/bb3de92f-14b9-4b26-a115-b1e65a25b406/videos/db707a51-7216-4a87-bb55-807a10537514/input/lesson.mp4"
}
```

O evento e idempotente por `eventId` e `videoId`. Reenvios de um processamento
ja concluido nao geram um novo ZIP.

## VideoProcessed

Topico produzido: `video.processing.completed`

```json
{
  "eventId": "c4900536-23c6-46da-9f80-cfb9be46d63e",
  "eventType": "VideoProcessed",
  "occurredAt": "2026-07-23T12:01:00Z",
  "videoId": "db707a51-7216-4a87-bb55-807a10537514",
  "outputObjectKey": "customers/bb3de92f-14b9-4b26-a115-b1e65a25b406/videos/db707a51-7216-4a87-bb55-807a10537514/output/frames.zip"
}
```

## VideoProcessingFailed

Topico produzido: `video.processing.failed`

```json
{
  "eventId": "445991ad-a32b-427f-af73-d3a64abcaa84",
  "eventType": "VideoProcessingFailed",
  "occurredAt": "2026-07-23T12:01:00Z",
  "videoId": "db707a51-7216-4a87-bb55-807a10537514",
  "failureReason": "Input video was not found"
}
```

Os motivos de falha publicados sao sanitizados e nao incluem mensagens internas,
credenciais, caminhos locais ou payloads recebidos.

## VideoProcessingRequestDeadLettered

Topico produzido: `video.processing.requested.dlq`

```json
{
  "eventId": "96152210-515e-31a8-b370-03113c1c8087",
  "eventType": "VideoProcessingRequestDeadLettered",
  "occurredAt": "2026-07-23T12:00:00Z",
  "sourceTopic": "video.processing.requested",
  "sourcePartition": 0,
  "sourceOffset": 42,
  "videoId": "db707a51-7216-4a87-bb55-807a10537514",
  "failureType": "RETRIES_EXHAUSTED"
}
```

`failureType` aceita `INVALID_REQUEST` ou `RETRIES_EXHAUSTED`. Em uma solicitacao
invalida, `videoId` pode ser `null` e a chave Kafka passa a identificar topico,
particao e offset de origem. O `eventId` e deterministico para essa mesma origem.
