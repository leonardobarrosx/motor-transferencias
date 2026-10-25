# ADR 0006 · Pix para outro banco como saga assíncrona, com reserva de saldo

**Status:** aceito

## Contexto

Até aqui todo Pix era entre duas contas do próprio motor e concluía na hora, numa transação. Mesmo assim, o
`motor-integracoes` gerava uma pacs.008 para o SPI. Isso estava errado no domínio: **Pix entre clientes do mesmo banco
não passa pelo SPI** ("on-us"). Só vai ao SPI o Pix cujo recebedor está em outra instituição, e aí a decisão final não é
nossa: o SPI e o banco recebedor respondem depois (pacs.002), aceitando ou recusando.

## Decisão

**1. Roteamento pelo DICT.** A chave Pix é procurada primeiro nas contas daqui; se não existir, no DICT (simulado por
uma tabela com chaves fictícias de outros bancos). Chave de fora → Pix para outra instituição.

**2. Estado PENDING com reserva de saldo.** Validado aqui (conta bloqueada, saldo disponível, limite do período) com a
conta pagadora travada, o Pix é gravado `PENDING` e **nenhum lançamento é feito**. O valor fica reservado:
`saldo disponível = saldo − soma dos Pix pendentes`, calculado com a conta travada, por qualquer Pix, interno ou
externo. O limite do período conta os pendentes.

**3. Saga pelo Kafka.**

```
motor ──TransferSubmitted──▶ pix.transfers ──▶ Camel: pacs.008 → SPI simulado
motor ◀──────────────────── pix.spi.results ◀── Camel: resposta (ACSC | RJCT + código ISO)
```

- **ACSC (liquidado):** débito na conta do cliente e crédito na **conta de liquidação** (espelho da conta PI do banco
  no Banco Central), status `COMPLETED`, evento `TransferCompleted`.
- **RJCT (recusado):** status `REJECTED` com `RECEIVER_REJECTED` e o código ISO (ex.: `AC03`), evento
  `TransferRejected`. **A compensação é liberar a reserva**: como o dinheiro nunca saiu, não há estorno a lançar.

**4. Idempotência do lado do motor.** A resposta do SPI é aplicada com as mesmas travas de qualquer Pix (pagador e
conta de liquidação, em ordem). A transferência é lida **depois** do lock; se já não está `PENDING`, a resposta é
repetida ou atrasada e é ignorada. O offset só é confirmado depois do commit.

**5. HTTP 202.** `POST /transfers` responde `202 Accepted` com `Location`; o comprovante mostra "em processamento"
e o app consulta até a decisão.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Debitar na hora e estornar na recusa | é o que muitos bancos fazem, mas a recusa vira dois lançamentos a mais (débito e estorno) e o extrato mostra um vai-e-volta; com reserva, o livro-razão só registra o que de fato saiu |
| Chamada síncrona ao SPI dentro da requisição | segura uma conexão e uma trava de linha esperando outro sistema; se o SPI demora, o banco inteiro fica lento |
| Two-phase commit entre motor e integrações | acopla os serviços e o broker numa transação distribuída; a saga com eventos e compensação é o padrão de mercado |
| Contrato `pix.spi.results` em XML pacs.002 | o motor teria que entender ISO 20022; o `motor-integracoes` é quem traduz, e o motor recebe um JSON simples |

## Consequências

- `ExternalPixIT`: o valor fica reservado (um Pix interno não consegue gastá-lo), ACSC liquida com dois lançamentos,
  RJCT libera a reserva sem mexer no saldo, resposta repetida não liquida duas vezes.
- `OutboxRelayIT`: a saga inteira pelo Kafka real (pedido publicado, resposta consumida, Pix concluído).
- `RotasPixTest`: Pix entre contas daqui não vai ao SPI; Pix para outro banco gera pacs.008 com o ISPB de destino e a
  resposta volta com a conta pagadora como chave.
- A conta de liquidação é uma **conta quente**: todo Pix externo liquidado a trava. Com volume real, seria dividida em
  subcontas (escolhidas por hash) ou teria o saldo calculado pelo livro-razão em vez de atualizado na hora.
- Fora do escopo: timeout de Pix pendente (consulta de status ao SPI depois de N segundos), Pix recebido de outros
  bancos (que reativaria a pacs.004 para devoluções externas) e o MED.
