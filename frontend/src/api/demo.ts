import { pixWindow } from "../lib/limits";
import { fromCents, toCents } from "../lib/money";
import { expire, session } from "./session";
import { ApiError } from "./types";
import type {
  Account, Contact, MotorApi, PixKey, RefundReason, RejectionReason, StatementLine, Transfer, TransferOutcome, TransferRequest,
} from "./types";

/**
 * Motor em memória para o modo demonstração (sem VITE_API_URL). Segue as regras
 * do backend: login, operações sempre na conta logada, consulta de chave Pix,
 * idempotência (mesma chave + mesmo pedido = mesma resposta) e recusas gravadas.
 */

interface StoredAccount extends Omit<Account, "balance" | "monthToDate"> {
  balanceCents: number;
  email: string;
}

const PASSWORD = "motor123";
const TOKEN_TTL_MS = 30 * 60 * 1000;

const REJECTION_MESSAGES: Record<RejectionReason, string> = {
  INSUFFICIENT_FUNDS: "Saldo insuficiente para esta transferência.",
  SOURCE_ACCOUNT_BLOCKED: "A conta de origem está bloqueada.",
  TARGET_ACCOUNT_BLOCKED: "A conta de destino não pode receber transferências.",
  LIMIT_EXCEEDED: "Valor acima do limite Pix disponível neste período.",
  RECEIVER_REJECTED: "O banco de quem ia receber recusou o Pix. Nenhum valor saiu da sua conta.",
};

/** DICT simulado: chaves de clientes de outros bancos (fictícios). */
const EXTERNAL_KEYS: Record<string, { holderName: string; document: string; institution: string; branch: string; number: string }> = {
  "maria@bancoexemplo.dev": { holderName: "Maria Oliveira", document: "***.852.963-**", institution: "Banco Exemplo", branch: "0101", number: "55501-2" },
  "encerrada@bancoexemplo.dev": { holderName: "Conta Encerrada", document: "***.357.456-**", institution: "Banco Exemplo", branch: "0101", number: "55599-9" },
};

/** Quanto o SPI simulado demora para responder. */
const SPI_LATENCY_MS = 2_000;

const DAYTIME_LIMIT = 500_000;   // R$ 5.000,00 em centavos
const NIGHTTIME_LIMIT = 100_000; // R$ 1.000,00

const SEED: StoredAccount[] = [
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001", email: "leonardo@motor.dev", holderName: "Leonardo Barros", document: "***.444.777-**", branch: "0001", number: "10001-7", pixKey: "leonardo@motor.dev", balanceCents: 2_500_000, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1002", email: "ana@motor.dev", holderName: "Ana Souza", document: "***.982.247-**", branch: "0001", number: "10002-5", pixKey: "ana@motor.dev", balanceCents: 320_050, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1003", email: "carlos@motor.dev", holderName: "Carlos Lima", document: "***.533.447-**", branch: "0001", number: "10003-3", pixKey: "+5583999990003", balanceCents: 87_000, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1004", email: "padaria@motor.dev", holderName: "Padaria Sol Nasce", document: "**.345.678/0001-**", branch: "0002", number: "20004-1", pixKey: "pix@padariasol.dev", balanceCents: 1_540_000, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1005", email: "bloqueada@motor.dev", holderName: "Conta Bloqueada", document: "***.883.667-**", branch: "0002", number: "20005-9", pixKey: "bloqueada@motor.dev", balanceCents: 10_000, status: "BLOCKED" },
];

const ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

function endToEndId(at: Date): string {
  const stamp = at.toISOString().replace(/\D/g, "").slice(0, 12);
  let suffix = "";
  for (let i = 0; i < 11; i++) suffix += ALPHANUMERIC[Math.floor(Math.random() * ALPHANUMERIC.length)];
  return `E99999999${stamp}${suffix}`;
}

function startOfMonthInBrasilia(now: Date): number {
  const [year, month] = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit" })
    .format(now).split("-").map(Number);
  return Date.UTC(year!, month! - 1, 1, 3); // 00:00 em Brasília (UTC-3)
}

const pause = (ms = 250 + Math.random() * 350) => new Promise((resolve) => setTimeout(resolve, ms));

export function createDemoApi(): MotorApi {
  const accounts = new Map(SEED.map((account) => [account.id, { ...account }]));
  const byKey = new Map<string, { transfer: Transfer; fingerprint: string }>();
  const transfers = new Map<string, Transfer>();
  const ledger: (StatementLine & { accountId: string; counterpartyPixKey: string })[] = [];

  /** O "JWT" do modo demonstração: demo.<accountId>. A conta sai do token, nunca do pedido. */
  function current(): StoredAccount {
    const token = session.token();
    const account = token?.startsWith("demo.") ? accounts.get(token.slice(5)) : undefined;
    if (!account) {
      expire();
      throw new ApiError({ status: 401, code: "UNAUTHENTICATED", detail: "Faça login para continuar." });
    }
    return account;
  }

  function view(account: StoredAccount): Account {
    const { balanceCents, email: _email, ...rest } = account;
    const since = startOfMonthInBrasilia(new Date());
    const entries = ledger.filter((e) => e.accountId === account.id && Date.parse(e.createdAt) >= since);
    const sum = (type: StatementLine["type"]) =>
      entries.filter((e) => e.type === type).reduce((total, e) => total + toCents(e.amount), 0);
    return {
      ...rest,
      balance: fromCents(balanceCents),
      monthToDate: { inflow: fromCents(sum("CREDIT")), outflow: fromCents(sum("DEBIT")), transfers: entries.length },
    };
  }

  function execute(payer: StoredAccount, key: string, request: TransferRequest, at = new Date()): TransferOutcome {
    const amountCents = toCents(request.amount);
    const fingerprint = JSON.stringify([payer.id, request.targetPixKey.trim(), amountCents, request.description?.trim() || null]);

    const existing = byKey.get(key);
    if (existing) {
      if (existing.fingerprint !== fingerprint) {
        throw new ApiError({ status: 422, code: "IDEMPOTENCY_KEY_REUSED", detail: "Esta Idempotency-Key já foi usada para uma transferência com outros dados." });
      }
      return outcomeOf(existing.transfer, true);
    }

    const payee = [...accounts.values()].find((a) => a.pixKey === request.targetPixKey.trim());
    const external = EXTERNAL_KEYS[request.targetPixKey.trim()];
    if (!payee && external) return toOtherBank(payer, key, fingerprint, request, external, at);
    if (!payee) throw new ApiError({ status: 404, code: "PIX_KEY_NOT_FOUND", detail: "Nenhuma conta encontrada para a chave Pix informada." });
    if (payer.id === payee.id) {
      throw new ApiError({ status: 422, code: "SAME_ACCOUNT_TRANSFER", detail: "A chave Pix de destino pertence à própria conta de origem." });
    }

    const window = pixWindow(at);
    const limit = window.period === "DAYTIME" ? DAYTIME_LIMIT : NIGHTTIME_LIMIT;
    const reason: RejectionReason | null =
      payer.status !== "ACTIVE" ? "SOURCE_ACCOUNT_BLOCKED"
        : payee.status !== "ACTIVE" ? "TARGET_ACCOUNT_BLOCKED"
          : payer.balanceCents - onHold(payer.id) < amountCents ? "INSUFFICIENT_FUNDS"
            : sentSince(payer.id, window.start) + amountCents > limit ? "LIMIT_EXCEEDED"
              : null;

    const transfer: Transfer = {
      id: crypto.randomUUID(),
      endToEndId: endToEndId(at),
      status: reason ? "REJECTED" : "COMPLETED",
      rejectionReason: reason,
      amount: fromCents(amountCents),
      description: request.description?.trim() || null,
      payer: { accountId: payer.id, holderName: payer.holderName, pixKey: payer.pixKey },
      payee: { accountId: payee.id, holderName: payee.holderName, pixKey: payee.pixKey },
      createdAt: at.toISOString(),
      kind: "PIX",
      originalTransferId: null,
      refundedAmount: "0.00",
    };
    byKey.set(key, { transfer, fingerprint });
    transfers.set(transfer.id, transfer);
    if (!reason) settle(transfer, payer, payee);
    return outcomeOf(transfer, false);
  }

  /** Reservado em Pix para outros bancos ainda sem resposta do SPI. */
  function onHold(accountId: string): number {
    let total = 0;
    for (const t of transfers.values()) {
      if (t.payer.accountId === accountId && t.status === "PENDING") total += toCents(t.amount);
    }
    return total;
  }

  /**
   * Pix para outro banco: fica PENDING com o valor reservado; o "SPI" responde depois.
   * Conta encerrada volta recusada (AC03) e a reserva é liberada sem mexer no saldo.
   */
  function toOtherBank(payer: StoredAccount, key: string, fingerprint: string, request: TransferRequest,
                       external: (typeof EXTERNAL_KEYS)[string], at: Date): TransferOutcome {
    const amountCents = toCents(request.amount);
    const window = pixWindow(at);
    const limit = window.period === "DAYTIME" ? DAYTIME_LIMIT : NIGHTTIME_LIMIT;
    const reason: RejectionReason | null =
      payer.status !== "ACTIVE" ? "SOURCE_ACCOUNT_BLOCKED"
        : payer.balanceCents - onHold(payer.id) < amountCents ? "INSUFFICIENT_FUNDS"
          : sentSince(payer.id, window.start) + amountCents > limit ? "LIMIT_EXCEEDED"
            : null;
    const pixKey = request.targetPixKey.trim();
    const transfer: Transfer = {
      id: crypto.randomUUID(),
      endToEndId: endToEndId(at),
      status: reason ? "REJECTED" : "PENDING",
      rejectionReason: reason,
      amount: fromCents(amountCents),
      description: request.description?.trim() || null,
      payer: { accountId: payer.id, holderName: payer.holderName, pixKey: payer.pixKey },
      payee: { accountId: null, holderName: external.holderName, pixKey, institution: external.institution },
      createdAt: at.toISOString(),
      kind: "PIX",
      originalTransferId: null,
      refundedAmount: "0.00",
      settledAt: null,
    };
    byKey.set(key, { transfer, fingerprint });
    transfers.set(transfer.id, transfer);
    if (!reason) {
      setTimeout(() => {
        const settledAt = new Date().toISOString();
        if (pixKey.startsWith("encerrada")) {
          transfers.set(transfer.id, { ...transfer, status: "REJECTED", rejectionReason: "RECEIVER_REJECTED", settledAt });
          return;
        }
        const done: Transfer = { ...transfer, status: "COMPLETED", settledAt };
        transfers.set(transfer.id, done);
        byKey.set(key, { transfer: done, fingerprint });
        payer.balanceCents -= amountCents;
        ledger.unshift({
          accountId: payer.id, counterpartyPixKey: pixKey, entryId: crypto.randomUUID(), transferId: done.id,
          endToEndId: done.endToEndId, type: "DEBIT", amount: done.amount, balanceAfter: fromCents(payer.balanceCents),
          counterpartyName: external.holderName, description: done.description, createdAt: done.createdAt,
        });
      }, SPI_LATENCY_MS);
    }
    return outcomeOf(transfer, false);
  }

  /** Quanto a conta já enviou em Pix (devoluções não contam) desde o início da janela. */
  function sentSince(accountId: string, since: Date): number {
    let total = 0;
    for (const t of transfers.values()) {
      if (t.payer.accountId === accountId && t.kind === "PIX" && (t.status === "COMPLETED" || t.status === "PENDING")
        && Date.parse(t.createdAt) >= since.getTime()) {
        total += toCents(t.amount);
      }
    }
    return total;
  }

  function refundedOf(originalId: string): number {
    let total = 0;
    for (const t of transfers.values()) {
      if (t.originalTransferId === originalId && t.status === "COMPLETED") total += toCents(t.amount);
    }
    return total;
  }

  function withRefunds(t: Transfer): Transfer {
    return t.kind === "PIX" ? { ...t, refundedAmount: fromCents(refundedOf(t.id)) } : t;
  }

  /** Devolução: só quem recebeu, até o valor original, em até 90 dias. */
  function refund(requester: StoredAccount, originalId: string, key: string, amount: string, reason: RefundReason): TransferOutcome {
    const amountCents = toCents(amount);
    const fingerprint = JSON.stringify(["REFUND", requester.id, originalId, amountCents, reason]);
    const existing = byKey.get(key);
    if (existing) {
      if (existing.fingerprint !== fingerprint) {
        throw new ApiError({ status: 422, code: "IDEMPOTENCY_KEY_REUSED", detail: "Esta Idempotency-Key já foi usada para uma transferência com outros dados." });
      }
      return outcomeOf(existing.transfer, true);
    }
    const original = transfers.get(originalId);
    if (!original || (original.payee.accountId !== requester.id && original.payer.accountId !== requester.id)) {
      throw new ApiError({ status: 404, code: "TRANSFER_NOT_FOUND", detail: "Transferência não encontrada." });
    }
    if (original.payee.accountId !== requester.id || original.kind !== "PIX" || original.status !== "COMPLETED") {
      throw new ApiError({ status: 422, code: "REFUND_NOT_ALLOWED", detail: "Só um Pix recebido e concluído pode ser devolvido." });
    }
    if (refundedOf(originalId) + amountCents > toCents(original.amount)) {
      throw new ApiError({ status: 422, code: "REFUND_AMOUNT_EXCEEDED", detail: "A soma das devoluções não pode passar do valor do Pix original." });
    }
    const originalPayer = accounts.get(original.payer.accountId!)!;
    const reasonCode: RejectionReason | null =
      requester.status !== "ACTIVE" ? "SOURCE_ACCOUNT_BLOCKED"
        : originalPayer.status !== "ACTIVE" ? "TARGET_ACCOUNT_BLOCKED"
          : requester.balanceCents < amountCents ? "INSUFFICIENT_FUNDS"
            : null;
    const at = new Date();
    const transfer: Transfer = {
      id: crypto.randomUUID(),
      endToEndId: "D" + endToEndId(at).slice(1),
      status: reasonCode ? "REJECTED" : "COMPLETED",
      rejectionReason: reasonCode,
      amount: fromCents(amountCents),
      description: "Devolução de Pix",
      payer: { accountId: requester.id, holderName: requester.holderName, pixKey: requester.pixKey },
      payee: { accountId: originalPayer.id, holderName: originalPayer.holderName, pixKey: originalPayer.pixKey },
      createdAt: at.toISOString(),
      kind: "REFUND",
      originalTransferId: originalId,
      refundedAmount: null,
    };
    byKey.set(key, { transfer, fingerprint });
    transfers.set(transfer.id, transfer);
    if (!reasonCode) settle(transfer, requester, originalPayer);
    return outcomeOf(transfer, false);
  }

  function settle(transfer: Transfer, payer: StoredAccount, payee: StoredAccount) {
    const amountCents = toCents(transfer.amount);
    payer.balanceCents -= amountCents;
    payee.balanceCents += amountCents;
    const line = (account: StoredAccount, type: StatementLine["type"], counterparty: StoredAccount) => ({
      accountId: account.id,
      counterpartyPixKey: counterparty.pixKey,
      entryId: crypto.randomUUID(),
      transferId: transfer.id,
      endToEndId: transfer.endToEndId,
      type,
      amount: transfer.amount,
      balanceAfter: fromCents(account.balanceCents),
      counterpartyName: counterparty.holderName,
      description: transfer.description,
      createdAt: transfer.createdAt,
    });
    ledger.unshift(line(payer, "DEBIT", payee), line(payee, "CREDIT", payer));
  }

  function outcomeOf(transfer: Transfer, replayed: boolean): TransferOutcome {
    if (transfer.status !== "REJECTED") return { kind: "completed", transfer: withRefunds(transfer), replayed };
    const reason = transfer.rejectionReason!;
    return { kind: "rejected", reason, detail: REJECTION_MESSAGES[reason], transferId: transfer.id, endToEndId: transfer.endToEndId, replayed };
  }

  // histórico para o painel não abrir vazio
  const day = 86_400_000;
  const history: [string, string, string, string, number][] = [
    ["padaria@motor.dev", "leonardo@motor.dev", "1840.00", "Projeto do site da padaria", 9 * day],
    ["leonardo@motor.dev", "ana@motor.dev", "1200.00", "Aluguel de outubro", 6 * day],
    ["carlos@motor.dev", "leonardo@motor.dev", "75.90", "Rachando a pizza", 3 * day],
    ["leonardo@motor.dev", "pix@padariasol.dev", "32.40", "Café e pão de queijo", day + 3_600_000],
    ["ana@motor.dev", "leonardo@motor.dev", "250.00", "Ingresso do show", 2 * 3_600_000],
  ];
  for (const [fromEmail, pixKey, amount, description, ago] of history) {
    const from = [...accounts.values()].find((a) => a.email === fromEmail)!;
    execute(from, crypto.randomUUID(), { targetPixKey: pixKey, amount, description }, new Date(Date.now() - ago));
  }

  return {
    mode: "demo",

    async login(email, password) {
      await pause(500);
      const account = [...accounts.values()].find((a) => a.email === email.trim().toLowerCase());
      if (!account || password !== PASSWORD) {
        throw new ApiError({ status: 401, code: "INVALID_CREDENTIALS", detail: "E-mail ou senha incorretos." });
      }
      return { token: `demo.${account.id}`, holderName: account.holderName, expiresAt: Date.now() + TOKEN_TTL_MS };
    },

    async me() {
      await pause();
      return view(current());
    },

    async statement(page, size, type) {
      await pause();
      const me = current();
      const lines = ledger
        .filter((e) => e.accountId === me.id && (!type || e.type === type))
        .map(({ accountId: _a, counterpartyPixKey: _k, ...line }) => line);
      return {
        content: lines.slice(page * size, page * size + size),
        page, size, totalElements: lines.length, totalPages: Math.ceil(lines.length / size),
      };
    },

    async contacts() {
      await pause();
      const me = current();
      const seen = new Map<string, Contact>();
      for (const e of ledger) {
        if (e.accountId === me.id && !seen.has(e.counterpartyPixKey)) {
          seen.set(e.counterpartyPixKey, { holderName: e.counterpartyName, pixKey: e.counterpartyPixKey, lastTransferAt: e.createdAt });
        }
      }
      return [...seen.values()].slice(0, 6);
    },

    async lookupPixKey(key) {
      await pause();
      const me = current();
      const account = [...accounts.values()].find((a) => a.pixKey === key.trim());
      const external = EXTERNAL_KEYS[key.trim()];
      if (!account && external) {
        return {
          pixKey: key.trim(), holderName: external.holderName, document: external.document,
          institution: external.institution, branch: external.branch,
          accountNumber: `•••${external.number.slice(-4)}`, acceptsTransfers: true,
        } satisfies PixKey;
      }
      if (!account) throw new ApiError({ status: 404, code: "PIX_KEY_NOT_FOUND", detail: "Nenhuma conta encontrada para a chave Pix informada." });
      if (account.id === me.id) throw new ApiError({ status: 422, code: "SAME_ACCOUNT_TRANSFER", detail: "A chave Pix de destino pertence à própria conta de origem." });
      return {
        pixKey: account.pixKey,
        holderName: account.holderName,
        document: account.document,
        institution: "Motor de Transferências",
        branch: account.branch,
        accountNumber: `•••${account.number.slice(-4)}`,
        acceptsTransfers: account.status === "ACTIVE",
      } satisfies PixKey;
    },

    async createTransfer(key, request) {
      await pause(700);
      return execute(current(), key, request);
    },

    async transfer(id) {
      await pause();
      const me = current();
      const transfer = transfers.get(id);
      if (!transfer || (transfer.payer.accountId !== me.id && transfer.payee.accountId !== me.id)) {
        throw new ApiError({ status: 404, code: "TRANSFER_NOT_FOUND", detail: "Transferência não encontrada." });
      }
      return withRefunds(transfer);
    },

    async refund(transferId, key, request) {
      await pause(700);
      return refund(current(), transferId, key, request.amount, request.reason);
    },

    async limits() {
      await pause();
      const me = current();
      const window = pixWindow(new Date());
      const limit = window.period === "DAYTIME" ? DAYTIME_LIMIT : NIGHTTIME_LIMIT;
      const used = sentSince(me.id, window.start);
      return {
        period: window.period,
        windowStart: window.start.toISOString(),
        windowEnd: window.end.toISOString(),
        limit: fromCents(limit),
        used: fromCents(used),
        available: fromCents(Math.max(0, limit - used)),
        daytimeLimit: fromCents(DAYTIME_LIMIT),
        nighttimeLimit: fromCents(NIGHTTIME_LIMIT),
      };
    },
  };
}
