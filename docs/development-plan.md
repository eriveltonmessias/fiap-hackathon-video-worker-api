# Video Worker API - Plano de Desenvolvimento

## Objetivo

Construir o worker assincrono da FIAP X responsavel por consumir pedidos de
processamento, gerar frames com FFmpeg, compactar o resultado, armazenar o ZIP e
publicar o resultado para o `video-manager-api`.

## Regras de execucao

Cada task usa uma branch propria e somente comeca depois do merge da anterior:

1. atualizar a `main`;
2. criar a branch indicada;
3. implementar apenas o escopo da task;
4. executar testes e criterios de aceite;
5. atualizar a documentacao afetada;
6. publicar a branch e pedir o merge;
7. aguardar o merge antes da proxima task.

## Contratos externos

Entrada Kafka:

- topico `video.processing.requested`;
- evento `VideoProcessingRequested`;
- campos `eventId`, `eventType`, `occurredAt`, `videoId`, `customerId`,
  `originalFilename` e `inputObjectKey`;
- chave Kafka: `videoId`.

Saidas Kafka:

- `VideoProcessed` em `video.processing.completed` com `eventId`, `eventType`,
  `occurredAt`, `videoId` e `outputObjectKey`;
- `VideoProcessingFailed` em `video.processing.failed` com `eventId`,
  `eventType`, `occurredAt`, `videoId` e `failureReason`;
- chave Kafka: `videoId`.

Storage:

- leitura do bucket `fiapx-videos-input` pela chave recebida;
- escrita do ZIP no bucket `fiapx-videos-output`;
- a chave de saida preserva cliente e video e termina em `frames.zip`.

## Tasks

### VW-001 - Estrutura do projeto

Branch: `feature/project-structure`

Objetivo:

- criar Spring Boot com Kotlin, Java 21 e Gradle Wrapper;
- adicionar MongoDB, Kafka, Actuator, validation e MinIO;
- separar `domain`, `application` e `infrastructure`;
- configurar MongoDB, Kafka, MinIO e porta `8083` por variaveis;
- expor health basico e criar teste de contexto isolado de dependencias externas.

Criterios de aceite:

- `./gradlew test` passa;
- aplicacao compila;
- configuracao nao contem segredo real;
- README explica a estrutura e a execucao inicial.

### VW-002 - Dominio do job

Branch: `feature/processing-job-domain`

Objetivo:

- criar `VideoProcessingJob` sem dependencias de framework;
- modelar status e transicoes internas;
- usar UUID diretamente para identificadores;
- criar VOs somente para valores com invariantes proprias.

Criterios de aceite:

- transicoes validas e invalidas estao testadas;
- estados terminais nao aceitam nova transicao;
- falha preserva motivo seguro e instante de termino.

### VW-003 - Persistencia MongoDB

Branch: `feature/mongodb-processing-job`

Objetivo:

- mapear jobs no MongoDB;
- criar indice unico por `videoId` para idempotencia;
- implementar repositorio da aplicacao;
- adicionar MongoDB ao Compose local.

Criterios de aceite:

- job e salvo e recuperado sem perder dados;
- duplicidade por video e impedida pelo banco;
- teste de integracao usa MongoDB real via Testcontainers.

### VW-004 - Storage MinIO

Branch: `feature/minio-storage`

Objetivo:

- baixar o video de entrada por streaming;
- enviar o ZIP de resultado por streaming;
- garantir os buckets de entrada e saida de forma idempotente;
- tratar objeto ausente e indisponibilidade do storage.

Criterios de aceite:

- download e upload funcionam com MinIO real;
- arquivos temporarios sao sempre removidos;
- chaves e credenciais nao aparecem em logs de erro.

### VW-005 - Consumo do pedido

Branch: `feature/processing-request-consumer`

Objetivo:

- consumir e validar `VideoProcessingRequested`;
- criar o job apenas uma vez;
- confirmar mensagens duplicadas sem reprocessar job concluido;
- iniciar a orquestracao por uma porta da aplicacao.

Criterios de aceite:

- payload valido cria job;
- evento e video duplicados sao idempotentes;
- payload invalido segue a politica de erro do consumer.

### VW-006 - Extracao de frames

Branch: `feature/ffmpeg-frame-extraction`

Objetivo:

- executar FFmpeg como processo externo com argumentos seguros;
- gerar frames JPEG em diretorio temporario isolado;
- impor timeout e limite de recursos configuraveis;
- coletar quantidade de frames e duracao do processamento.

Criterios de aceite:

- video valido gera frames;
- processo com erro ou timeout termina como falha;
- nenhum argumento do arquivo e interpretado pelo shell;
- teste usa um video pequeno e FFmpeg real.

### VW-007 - ZIP e pipeline completo

Branch: `feature/video-processing-pipeline`

Objetivo:

- compactar frames com APIs nativas de ZIP;
- integrar download, FFmpeg, compactacao e upload;
- atualizar cada etapa do job no MongoDB;
- limpar arquivos temporarios em sucesso e falha.

Criterios de aceite:

- ZIP contem todos os frames esperados;
- resultado fica no bucket e chave corretos;
- job termina `COMPLETED` com metadata consistente.

### VW-008 - Publicacao de resultados

Branch: `feature/processing-result-outbox`

Objetivo:

- persistir resultado pendente junto com a conclusao do job;
- publicar `VideoProcessed` ou `VideoProcessingFailed` no topico correto;
- marcar publicacao somente apos confirmacao do Kafka;
- manter retries sem duplicar o processamento do video.

Criterios de aceite:

- sucesso e falha geram contratos compativeis com o manager;
- falha de Kafka mantem resultado pendente;
- dispatcher tolera publicacao repetida.

### VW-009 - Retry e DLQ

Branch: `feature/consumer-retry-dlq`

Objetivo:

- classificar falhas transitorias e permanentes;
- aplicar retry limitado no consumo;
- publicar mensagens irrecuperaveis em `video.processing.requested.dlq`;
- registrar tentativas no job.

Criterios de aceite:

- falha transitoria e reexecutada;
- falha permanente publica resultado de falha sem loop infinito;
- payload ilegivel chega a DLQ com metadata segura.

### VW-010 - Observabilidade e EKS

Branch: `feature/observability`

Objetivo:

- adicionar metricas Prometheus e logs ECS;
- incluir `videoId` e `eventId` no contexto de log;
- expor liveness e readiness para MongoDB, Kafka e MinIO;
- configurar shutdown gracioso para consumo Kafka e FFmpeg.

Criterios de aceite:

- health distingue vida e prontidao;
- metricas medem jobs, duracao, frames, falhas e retries;
- probes e terminacao de pod estao documentados.

### VW-011 - Fluxo de integracao

Branch: `feature/integration-flow-tests`

Objetivo:

- cobrir Kafka, MongoDB, MinIO e FFmpeg em um fluxo real;
- validar sucesso, falha, duplicidade e limpeza temporaria;
- confirmar compatibilidade dos eventos com o manager.

Criterios de aceite:

- suite sobe dependencias com Testcontainers;
- evento de entrada produz ZIP e evento de sucesso;
- video invalido produz evento de falha;
- evento duplicado nao repete processamento concluido.

### VW-012 - Container, CI e documentacao final

Branch: `feature/delivery-pipeline`

Objetivo:

- criar imagem multi-stage com Java 21 e FFmpeg;
- executar como usuario nao-root;
- criar CI para build, testes e imagem;
- finalizar README, contratos e variaveis para EKS.

Criterios de aceite:

- `./gradlew build` passa;
- imagem contem FFmpeg e executa sem root;
- pipeline roda em push e pull request;
- uma pessoa nova consegue executar o worker pelo README.

## Definition of Done

- escopo da branch corresponde a uma task;
- codigo compila e testes proporcionais ao risco passam;
- `git diff --check` nao encontra problemas;
- contratos e documentacao afetados estao atualizados;
- nenhum segredo real esta versionado;
- branch e publicada e o merge e pedido explicitamente.
