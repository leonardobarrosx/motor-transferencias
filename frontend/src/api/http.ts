import { expire, session } from "./session";
import { ApiError, NetworkError } from "./types";
import type {
  Account, Contact, EntryType, MotorApi, Page, PixKey, Problem, StatementLine, Transfer, TransferOutcome, TransferRequest,
} from "./types";

export function createHttpApi(baseUrl: string): MotorApi {
  const base = baseUrl.replace(/\/+$/, "");

  async function send(path: string, init: RequestInit = {}, authenticated = true): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json, application/problem+json");
    const token = session.token();
    if (authenticated && token) headers.set("Authorization", `Bearer ${token}`);

    let response: Response;
    try {
      response = await fetch(`${base}${path}`, { ...init, headers });
    } catch {
      throw new NetworkError();
    }
    // token vencido ou inválido: encerra a sessão e a tela volta para o login
    if (authenticated && response.status === 401) expire();
    return response;
  }

  async function problemOf(response: Response): Promise<Problem> {
    try {
      return (await response.json()) as Problem;
    } catch {
      return { status: response.status, title: response.statusText };
    }
  }

  async function json<T>(path: string): Promise<T> {
    const response = await send(path);
    if (!response.ok) throw new ApiError(await problemOf(response));
    return (await response.json()) as T;
  }

  return {
    mode: "http",

    async login(email, password) {
      const response = await send("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      }, false);
      if (!response.ok) throw new ApiError(await problemOf(response));
      const body = (await response.json()) as { accessToken: string; expiresIn: number; holderName: string };
      return { token: body.accessToken, holderName: body.holderName, expiresAt: Date.now() + body.expiresIn * 1000 };
    },

    me: () => json<Account>("/api/v1/me"),

    statement: (page, size, type?: EntryType) =>
      json<Page<StatementLine>>(`/api/v1/me/statement?page=${page}&size=${size}${type ? `&type=${type}` : ""}`),

    contacts: () => json<Contact[]>("/api/v1/me/contacts"),

    lookupPixKey: (key) => json<PixKey>(`/api/v1/pix/keys/${encodeURIComponent(key.trim())}`),

    transfer: (id) => json<Transfer>(`/api/v1/transfers/${encodeURIComponent(id)}`),

    async createTransfer(idempotencyKey: string, request: TransferRequest): Promise<TransferOutcome> {
      const response = await send("/api/v1/transfers", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
        body: JSON.stringify(request),
      });
      const replayed = response.headers.get("Idempotent-Replayed") === "true";

      if (response.status === 200 || response.status === 201) {
        return { kind: "completed", transfer: await response.json(), replayed };
      }
      const problem = await problemOf(response);
      if (response.status === 422 && problem.code === "TRANSFER_REJECTED") {
        return {
          kind: "rejected",
          reason: problem.reason as Extract<TransferOutcome, { kind: "rejected" }>["reason"],
          detail: problem.detail ?? "Transferência recusada.",
          transferId: String(problem.transferId),
          endToEndId: String(problem.endToEndId),
          replayed,
        };
      }
      throw new ApiError(problem);
    },
  };
}
