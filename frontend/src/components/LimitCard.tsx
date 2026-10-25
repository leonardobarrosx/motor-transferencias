import { useEffect, useState } from "react";
import { api, type PixLimits } from "../api";
import { toCents } from "../lib/money";
import { Money } from "./Money";

const HOUR = new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", hour: "2-digit", minute: "2-digit" });

/** Limite Pix do período, como os bancos mostram: quanto ainda dá para enviar até a janela virar. */
export function LimitCard() {
  const [limits, setLimits] = useState<PixLimits | null>(null);
  useEffect(() => { api.limits().then(setLimits).catch(() => setLimits(null)); }, []);
  if (!limits) return null;

  const used = toCents(limits.used);
  const limit = toCents(limits.limit);
  const night = limits.period === "NIGHTTIME";
  return (
    <section className="card limit-card" aria-labelledby="limite">
      <div className="card__head">
        <h2 id="limite">Limite Pix</h2>
        <span className={`tag ${night ? "tag--night" : ""}`}>{night ? "Noturno · 20h–6h" : "Diurno · 6h–20h"}</span>
      </div>
      <div className="card__body limit-card__body">
        <div>
          <span className="muted">Disponível agora</span>
          <strong className="limit-card__value"><Money cents={toCents(limits.available)} /></strong>
        </div>
        <div className="meter" role="meter" aria-valuemin={0} aria-valuemax={limit} aria-valuenow={used}
          aria-label={`Usado ${Math.round((used / limit) * 100)}% do limite`}>
          <span style={{ width: `${Math.min(100, (used / limit) * 100)}%` }} />
        </div>
        <p className="muted limit-card__note">
          Usado <Money cents={used} /> de <Money cents={limit} />. Renova às {HOUR.format(new Date(limits.windowEnd))}.
          {night && " O limite noturno é menor por segurança, como determina o Banco Central."}
        </p>
      </div>
    </section>
  );
}
