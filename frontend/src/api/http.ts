import { ApiError, NetworkError } from "./types";
import type { Account, MotorApi, Page, Problem, StatementLine, TransferOutcome, TransferRequest } from "./types";

export function createHttpApi(baseUrl: string): MotorApi {
  const base = baseUrl.replace(/\/+$/, "");

  async function send(path: string, init?: RequestInit): Promise<Response> {
    try {
      return await fetch(`${base}${path}`, {
        ...init,
        headers: { Accept: "application/json, application/problem+json", ...init?.headers },
      });
    } catch {
      throw new NetworkError();
    }
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

    listAccounts: () => json<Account[]>("/api/v1/accounts"),

    getAccount: (id) => json<Account>(`/api/v1/accounts/${encodeURIComponent(id)}`),

    getStatement: (id, page, size) =>
      json<Page<StatementLine>>(`/api/v1/accounts/${encodeURIComponent(id)}/statement?page=${page}&size=${size}`),

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
