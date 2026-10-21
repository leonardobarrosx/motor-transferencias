import { createDemoApi } from "./demo";
import { createHttpApi } from "./http";
import type { MotorApi } from "./types";

const baseUrl = import.meta.env.VITE_API_URL?.trim();

/** Com VITE_API_URL fala com o backend; sem ela, roda o motor em memória. */
export const api: MotorApi = baseUrl ? createHttpApi(baseUrl) : createDemoApi();

export * from "./types";
