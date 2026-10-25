# ADR 0004 · Eventos com outbox transacional, Kafka e Apache Camel

**Status:** aceito

## Contexto

Depois de um Pix, outras coisas precisam acontecer: avisar quem pagou e quem recebeu, enviar a ordem ao sistema de
liquidação (no Pix real, o SPI do Banco Central, em mensagens ISO 20022) e gerar a conciliação. Nenhuma delas pode
atrasar nem derrubar a transferência, e a transferência não pode acontecer sem que elas aconteçam depois.

## Decisão

**1. O núcleo financeiro continua num serviço só.** Débito, crédito, lançamentos e a ordem de transferência ficam
numa única transação ACID no `motor-transferencias`. Dividir as contas em serviços diferentes trocaria uma transação de
banco por uma saga com compensação: mais código, mais estados intermediários e nenhum ganho para o problema.

**2. O resto vira um microsserviço que reage a eventos:** `motor-integracoes`, ligado ao motor só pelo Kafka. Ele pode
cair, ser reimplantado ou ficar lento sem que nenhum Pix deixe de ser concluído; quando volta, continua do offset
onde parou.

**3. Outbox transacional em vez de publicar direto no Kafka.** Publicar dentro do método da transferência teria duas
falhas possíveis: publicar e a transação dar rollback (evento de algo que não aconteceu), ou commitar e o Kafka
estar fora (transferência sem evento). Com o outbox, o evento é uma linha gravada **na mesma transação**; um relay lê a
tabela e publica. Rollback desfaz os dois; Kafka fora do ar só atrasa a publicação.

- O relay usa `SELECT ... FOR UPDATE SKIP LOCKED`: duas instâncias pegam lotes diferentes sem esperar uma pela outra.
- Só marca como publicado o que o broker confirmou (`acks=all`, produtor idempotente).
- Garantia: **pelo menos uma vez**. Se o Kafka confirmar e o commit falhar, o evento sai de novo.

**4. Consumidor idempotente.** Como o mesmo evento pode chegar duas vezes, o Camel deduplica por `eventId`
(*Idempotent Consumer*). É a mesma ideia da `Idempotency-Key` da API, agora entre serviços.

**5. Apache Camel para as rotas de integração.** Cada responsabilidade é um padrão de integração (EIP) conhecido, e as
rotas leem como o desenho da arquitetura:

| EIP | Onde |
|---|---|
| Idempotent Consumer | descarta `eventId` repetido |
| Content-Based Router | `TransferCompleted` e `TransferRejected` seguem caminhos diferentes |
| Multicast | um Pix liquidado alimenta notificação, SPI e conciliação |
| Message Translator | evento → notificação; evento → XML ISO 20022 pacs.008 |
| Splitter | uma transferência gera uma notificação por pessoa |
| Aggregator | Pix liquidados agrupados num CSV de conciliação (fecha por tamanho ou por tempo) |
| Dead Letter Channel | 3 tentativas com backoff exponencial; esgotou, a mensagem original vai para `pix.transfers.dlq` e o consumo segue |

**6. Kafka como broker.** É o que a empresa-alvo mais usa hoje, e o modelo de log com offsets permite reprocessar
eventos e acrescentar consumidores novos (antifraude, analytics) sem mexer no produtor. RabbitMQ atenderia bem a
notificação, mas não guarda histórico para reprocessamento.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Publicar no Kafka dentro da transação | a dupla escrita (banco + broker) não é atômica; ver item 3 |
| Transação distribuída (XA / 2PC) | lenta, frágil e mal suportada pelo Kafka |
| Debezium (CDC lendo o WAL do Postgres) | é o passo seguinte natural: tira o polling. Exige Kafka Connect, infraestrutura demais para a demonstração |
| Spring Kafka com `@KafkaListener` no consumidor | funciona; o Camel foi escolhido porque as rotas de integração (roteamento, tradução, agregação, DLQ) são o forte dele |
| Microsserviço por conta ou por lado da transferência | perderia a transação ACID; exigiria saga |

## Consequências

- A transferência não depende do Kafka: com `OUTBOX_RELAY_ENABLED=false` (padrão), a API funciona e os eventos ficam
  no outbox até o relay ser ligado. É assim no deploy gratuito.
- As integrações são eventualmente consistentes: a notificação chega instantes depois do Pix, não junto.
- Testes: `OutboxRelayIT` (Postgres + Kafka reais) prova transferência concluída e recusada publicadas com a chave
  certa, e nenhuma publicação de transferência que falhou; `TransferConcurrencyIT` prova que, de 20 requisições com a
  mesma chave, só um evento existe; `RotasPixTest` prova roteamento, deduplicação, agregação e DLQ.
- Lição de operação aprendida no caminho: um consumidor que assina um tópico antes de ele ter todas as partições só
  as descobre no próximo refresh de metadados (5 min por padrão). Por isso os tópicos são criados pela
  infraestrutura (`kafka-init`), a criação automática fica desligada no broker e o refresh do consumidor foi para 30 s.
