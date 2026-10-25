import { describe, expect, it } from "vitest";
import { pixWindow } from "./limits";

describe("pixWindow (horário de Brasília)", () => {
  it.each([
    ["2026-10-25T09:00:00Z", "DAYTIME", "2026-10-25T09:00:00.000Z", "2026-10-25T23:00:00.000Z"],   // 06:00
    ["2026-10-25T22:59:59Z", "DAYTIME", "2026-10-25T09:00:00.000Z", "2026-10-25T23:00:00.000Z"],   // 19:59
    ["2026-10-25T23:00:00Z", "NIGHTTIME", "2026-10-25T23:00:00.000Z", "2026-10-26T09:00:00.000Z"], // 20:00
    ["2026-10-26T05:00:00Z", "NIGHTTIME", "2026-10-25T23:00:00.000Z", "2026-10-26T09:00:00.000Z"], // 02:00
  ])("%s → %s", (now, period, start, end) => {
    const w = pixWindow(new Date(now));
    expect(w.period).toBe(period);
    expect(w.start.toISOString()).toBe(start);
    expect(w.end.toISOString()).toBe(end);
  });
});
