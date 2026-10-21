import { fromCents, toCents } from "../lib/money";
import { ApiError } from "./types";
import type {
  Account,
  MotorApi,
  RejectionReason,
  StatementLine,
  Transfer,
  TransferOutcome,
  TransferRequest,
} from "./types";

/**
 * Motor em memória para o modo demonstração (sem VITE_API_URL). Segue as mesmas
 * regras do backend, inclusive a idempotência: a mesma chave com o mesmo pedido
 * devolve a mesma resposta, e com outro pedido é recusada.
 */

interface StoredAccount extends Omit<Account, "balance" | "monthToDate"> {
  balanceCents: number;
}

interface StoredTransfer {
  transfer: Transfer;
  fingerprint: string;
}

const REJECTION_MESSAGES: Record<RejectionReason, string> = {
  INSUFFICIENT_FUNDS: "Saldo insuficiente para esta transferência.",
  SOURCE_ACCOUNT_BLOCKED: "A conta de origem está bloqueada.",
  TARGET_ACCOUNT_BLOCKED: "A conta de destino não pode receber transferências.",
};

const SEED: StoredAccount[] = [
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001", holderName: "Leonardo Barros", document: "***.444.777-**", branch: "0001", number: "10001-7", pixKey: "leonardo@motor.dev", balanceCents: 2_500_000, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1002", holderName: "Ana Souza", document: "***.982.247-**", branch: "0001", number: "10002-5", pixKey: "ana@motor.dev", balanceCents: 320_050, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1003", holderName: "Carlos Lima", document: "***.533.447-**", branch: "0001", number: "10003-3", pixKey: "+5583999990003", balanceCents: 87_000, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1004", holderName: "Padaria Sol Nasce", document: "**.345.678/0001-**", branch: "0002", number: "20004-1", pixKey: "pix@padariasol.dev", balanceCents: 1_540_000, status: "ACTIVE" },
  { id: "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1005", holderName: "Conta Bloqueada", document: "***.883.667-**", branch: "0002", number: "20005-9", pixKey: "bloqueada@motor.dev", balanceCents: 10_000, status: "BLOCKED" },
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
    .format(now)
    .split("-")
    .map(Number);
  return Date.UTC(year!, month! - 1, 1, 3); // 00:00 em Brasília (UTC-3)
}

const pause = () => new Promise((resolve) => setTimeout(resolve, 250 + Math.random() * 350));

export function createDemoApi(): MotorApi {
  const accounts = new Map(SEED.map((account) => [account.id, { ...account }]));
  const byKey = new Map<string, StoredTransfer>();
  const ledger: (StatementLine & { accountId: string })[] = [];

  function find(id: string): StoredAccount {
    const account = accounts.get(id);
    if (!account) throw new ApiError({ status: 404, code: "ACCOUNT_NOT_FOUND", detail: "Conta não encontrada." });
    return account;
  }

  function view(account: StoredAccount, withMonth: boolean): Account {
    const { balanceCents, ...rest } = account;
    let monthToDate: Account["monthToDate"] = null;
    if (withMonth) {
      const since = startOfMonthInBrasilia(new Date());
      const entries = ledger.filter((e) => e.accountId === account.id && Date.parse(e.createdAt) >= since);
      const sum = (type: StatementLine["type"]) =>
        entries.filter((e) => e.type === type).reduce((total, e) => total + toCents(e.amount), 0);
      monthToDate = { inflow: fromCents(sum("CREDIT")), outflow: fromCents(sum("DEBIT")), transfers: entries.length };
    }
    return { ...rest, balance: fromCents(balanceCents), monthToDate };
  }

  function execute(key: string, request: TransferRequest, at = new Date()): TransferOutcome {
    const amountCents = toCents(request.amount);
    const fingerprint = JSON.stringify([request.sourceAccountId, request.targetPixKey.trim(), amountCents, request.description?.trim() || null]);

    const existing = byKey.get(key);
    if (existing) {
      if (existing.fingerprint !== fingerprint) {
        throw new ApiError({ status: 422, code: "IDEMPOTENCY_KEY_REUSED", detail: "Esta Idempotency-Key já foi usada para uma transferência com outros dados." });
      }
      return outcomeOf(existing.transfer, true);
    }

    const payee = [...accounts.values()].find((a) => a.pixKey === request.targetPixKey.trim());
    if (!payee) throw new ApiError({ status: 404, code: "PIX_KEY_NOT_FOUND", detail: "Nenhuma conta encontrada para a chave Pix informada." });
    const payer = find(request.sourceAccountId);
    if (payer.id === payee.id) {
      throw new ApiError({ status: 422, code: "SAME_ACCOUNT_TRANSFER", detail: "A chave Pix de destino pertence à própria conta de origem." });
    }

    const reason: RejectionReason | null =
      payer.status !== "ACTIVE" ? "SOURCE_ACCOUNT_BLOCKED"
        : payee.status !== "ACTIVE" ? "TARGET_ACCOUNT_BLOCKED"
          : payer.balanceCents < amountCents ? "INSUFFICIENT_FUNDS"
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
    };
    byKey.set(key, { transfer, fingerprint });

    if (!reason) {
      payer.balanceCents -= amountCents;
      payee.balanceCents += amountCents;
      const line = (account: StoredAccount, type: StatementLine["type"], counterparty: StoredAccount) => ({
        accountId: account.id,
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
    return outcomeOf(transfer, false);
  }

  function outcomeOf(transfer: Transfer, replayed: boolean): TransferOutcome {
    if (transfer.status === "COMPLETED") return { kind: "completed", transfer, replayed };
    const reason = transfer.rejectionReason!;
    return { kind: "rejected", reason, detail: REJECTION_MESSAGES[reason], transferId: transfer.id, endToEndId: transfer.endToEndId, replayed };
  }

  // um pouco de histórico para o painel não abrir vazio
  const history: [string, string, string, string, number][] = [
    ["0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1004", "leonardo@motor.dev", "1840.00", "Projeto site da padaria", 9],
    ["0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001", "ana@motor.dev", "1200.00", "Aluguel outubro", 6],
    ["0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1003", "leonardo@motor.dev", "75.90", "Rachando a pizza", 3],
    ["0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001", "pix@padariasol.dev", "32.40", "Café e pão de queijo", 1],
  ];
  for (const [source, pixKey, amount, description, daysAgo] of history) {
    execute(crypto.randomUUID(), { sourceAccountId: source, targetPixKey: pixKey, amount, description }, new Date(Date.now() - daysAgo * 86_400_000));
  }

  return {
    mode: "demo",

    async listAccounts() {
      await pause();
      return [...accounts.values()].sort((a, b) => a.holderName.localeCompare(b.holderName)).map((a) => view(a, false));
    },

    async getAccount(id) {
      await pause();
      return view(find(id), true);
    },

    async getStatement(id, page, size) {
      await pause();
      find(id);
      const lines = ledger.filter((e) => e.accountId === id).map(({ accountId: _, ...line }) => line);
      return {
        content: lines.slice(page * size, page * size + size),
        page,
        size,
        totalElements: lines.length,
        totalPages: Math.ceil(lines.length / size),
      };
    },

    async createTransfer(key, request) {
      await pause();
      return execute(key, request);
    },
  };
}
