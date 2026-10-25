import type { Money } from "../lib/money";

export interface Account {
  id: string;
  holderName: string;
  document: string;
  branch: string;
  number: string;
  pixKey: string;
  balance: Money;
  status: "ACTIVE" | "BLOCKED";
  monthToDate: { inflow: Money; outflow: Money; transfers: number } | null;
}

export type EntryType = "DEBIT" | "CREDIT";

export interface StatementLine {
  entryId: string;
  transferId: string;
  endToEndId: string;
  type: EntryType;
  amount: Money;
  balanceAfter: Money;
  counterpartyName: string;
  description: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Contact {
  holderName: string;
  pixKey: string;
  lastTransferAt: string;
}

export interface PixKey {
  pixKey: string;
  holderName: string;
  document: string;
  institution: string;
  branch: string;
  accountNumber: string;
  acceptsTransfers: boolean;
}

export interface Party {
  accountId: string;
  holderName: string;
  pixKey: string;
}

export type RejectionReason = "INSUFFICIENT_FUNDS" | "SOURCE_ACCOUNT_BLOCKED" | "TARGET_ACCOUNT_BLOCKED";

export interface Transfer {
  id: string;
  endToEndId: string;
  status: "COMPLETED" | "REJECTED";
  rejectionReason: RejectionReason | null;
  amount: Money;
  description: string | null;
  payer: Party;
  payee: Party;
  createdAt: string;
}

export interface TransferRequest {
  targetPixKey: string;
  amount: Money;
  description?: string;
}

export type TransferOutcome =
  | { kind: "completed"; transfer: Transfer; replayed: boolean }
  | { kind: "rejected"; reason: RejectionReason; detail: string; transferId: string; endToEndId: string; replayed: boolean };

export interface Session {
  token: string;
  holderName: string;
  expiresAt: number;
}

/** Problem Details (RFC 9457) como o backend devolve. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
  [extra: string]: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? `Erro ${problem.status}`);
    this.name = "ApiError";
    this.status = problem.status;
    this.code = problem.code ?? "UNKNOWN";
  }
}

/** A requisição pode ou não ter chegado ao servidor: só a mesma Idempotency-Key torna o reenvio seguro. */
export class NetworkError extends Error {
  constructor(message = "Sem resposta do servidor. Verifique sua conexão.") {
    super(message);
    this.name = "NetworkError";
  }
}

export interface MotorApi {
  readonly mode: "http" | "demo";
  login(email: string, password: string): Promise<Session>;
  me(): Promise<Account>;
  statement(page: number, size: number, type?: EntryType): Promise<Page<StatementLine>>;
  contacts(): Promise<Contact[]>;
  lookupPixKey(key: string): Promise<PixKey>;
  createTransfer(idempotencyKey: string, request: TransferRequest): Promise<TransferOutcome>;
  transfer(id: string): Promise<Transfer>;
}
