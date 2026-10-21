import { describe, expect, it } from "vitest";
import { centsFromDigits, formatAmountInput, fromCents, splitBRL, toCents } from "./money";

describe("money", () => {
  it("converte a string da API para centavos sem ponto flutuante", () => {
    expect(toCents("150.75")).toBe(15075);
    expect(toCents("0.1")).toBe(10);
    expect(toCents("3200.5")).toBe(320050);
    expect(toCents("25000")).toBe(2500000);
    expect(toCents("-7.50")).toBe(-750);
  });

  it("recusa formatos que não são dinheiro", () => {
    expect(() => toCents("1,50")).toThrow();
    expect(() => toCents("10.001")).toThrow();
    expect(() => toCents("abc")).toThrow();
  });

  it("volta para a string da API", () => {
    expect(fromCents(15075)).toBe("150.75");
    expect(fromCents(5)).toBe("0.05");
    expect(fromCents(-750)).toBe("-7.50");
    expect(toCents(fromCents(123456789))).toBe(123456789);
  });

  it("soma que daria erro em float dá certo em centavos", () => {
    expect(fromCents(toCents("0.1") + toCents("0.2"))).toBe("0.30");
  });

  it("separa reais e centavos para a tipografia", () => {
    expect(splitBRL(2484925)).toEqual({ units: "24.849", cents: "25" });
    expect(splitBRL(5)).toEqual({ units: "0", cents: "05" });
  });

  it("máscara de caixa eletrônico", () => {
    expect(centsFromDigits("1")).toBe(1);
    expect(centsFromDigits("150,75")).toBe(15075);
    expect(centsFromDigits("R$ 0,00")).toBe(0);
    expect(formatAmountInput(15075)).toBe("150,75");
    expect(formatAmountInput(123456789)).toBe("1.234.567,89");
    expect(formatAmountInput(0)).toBe("");
  });
});
