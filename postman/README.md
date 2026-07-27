# Fluxo completo pelo Kong

A collection `FIAP X - Fluxo Ponta a Ponta.postman_collection.json` já aponta
para o Load Balancer atual do Kong:

```text
http://ab1488290cddc4521b73d0707198d030-1f6f0932cb0d2ea5.elb.us-east-1.amazonaws.com
```

Ela usa somente endpoints públicos:

```text
Kong -> Customer Auth API
     -> Video Manager API -> Kafka -> Video Worker API
                          <- Kafka <-
     -> Grafana
```

Prometheus, Loki, Jaeger, Actuator, bancos, Kafka e o Worker permanecem
inacessíveis externamente.

## Executar

1. Importe `FIAP X - Fluxo Ponta a Ponta.postman_collection.json` no Postman.
2. Em **Variables**, troque `notificationEmail` pelo Gmail que receberá a
   notificação.
3. Execute a collection inteira no **Collection Runner**, na ordem original.

A collection:

1. valida o Grafana pelo Kong;
2. cria um alias Gmail único, cadastra o cliente e salva o UUID;
3. faz login e salva o JWT;
4. envia `assets/sample-video.mp4` e salva o UUID do vídeo;
5. consulta o processamento até chegar a `PROCESSED`;
6. baixa o ZIP;
7. confirma o vídeo na listagem e deixa IDs para pesquisa no Grafana.

O arquivo do upload já está configurado pelo caminho absoluto desta máquina. Se
o Postman solicitar permissão ou não localizar o arquivo, selecione manualmente:

```text
postman/assets/sample-video.mp4
```

No envio manual, repita `02 - Processamento / 02 - Aguardar processamento`
enquanto o status estiver pendente. No Collection Runner, o polling é
automático.

## Grafana

Abra:

```text
http://ab1488290cddc4521b73d0707198d030-1f6f0932cb0d2ea5.elb.us-east-1.amazonaws.com/grafana
```

Usuário:

```text
admin
```

Senha:

```bash
kubectl get secret monitoring-grafana \
  --namespace observability \
  --output jsonpath='{.data.admin-password}' |
  base64 --decode
echo
```

Use `customerId`, `videoId` e `lastRequestId`, preenchidos nas variáveis da
collection, para demonstrar correlação entre métricas, logs e traces.
