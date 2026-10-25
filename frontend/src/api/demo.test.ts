import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createDemoApi } from "./demo";
import { session } from "./session";

/** O modo demonstração imita a saga do Pix para outro banco: pendente, reservado e decidido depois. */
describe("Pix para outro banco no modo demonstração", () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ["setTimeout"] }));
  afterEach(() => vi.useRealTimers());

  async function loggedIn() {
    const api = createDemoApi();
    const login = api.login("carlos@motor.dev", "motor123");
    await vi.runAllTimersAsync();
    session.set(await login);
    return api;
  }

  async function settle<T>(promise: Promise<T>): Promise<T> {
    await vi.advanceTimersByTimeAsync(1_000);
    return promise;
  }

  it("fica pendente, reserva o valor e conclui quando o SPI responde", async () => {
    const api = await loggedIn();
    const outcome = await settle(api.createTransfer(crypto.randomUUID(), { targetPixKey: "maria@bancoexemplo.dev", amount: "500.00" }));
    if (outcome.kind !== "completed") throw new Error("esperava aceito");
    expect(outcome.transfer.status).toBe("PENDING");
    expect(outcome.transfer.payee.institution).toBe("Banco Exemplo");

    // R$ 794,10 de saldo (já com o histórico de exemplo), R$ 500 reservados: um Pix de R$ 400 não cabe
    const second = await settle(api.createTransfer(crypto.randomUUID(), { targetPixKey: "ana@motor.dev", amount: "400.00" }));
    expect(second.kind === "rejected" && second.reason).toBe("INSUFFICIENT_FUNDS");

    await vi.advanceTimersByTimeAsync(2_500);
    const done = await settle(api.transfer(outcome.transfer.id));
    expect(done.status).toBe("COMPLETED");
  });

  it("recusa do banco recebedor libera a reserva sem mexer no saldo", async () => {
    const api = await loggedIn();
    const before = (await settle(api.me())).balance;
    const outcome = await settle(api.createTransfer(crypto.randomUUID(), { targetPixKey: "encerrada@bancoexemplo.dev", amount: "100.00" }));
    if (outcome.kind !== "completed") throw new Error("esperava aceito");

    await vi.advanceTimersByTimeAsync(2_500);
    const refused = await settle(api.transfer(outcome.transfer.id));
    expect(refused.status).toBe("REJECTED");
    expect(refused.rejectionReason).toBe("RECEIVER_REJECTED");
    expect((await settle(api.me())).balance).toBe(before);
  });
});
