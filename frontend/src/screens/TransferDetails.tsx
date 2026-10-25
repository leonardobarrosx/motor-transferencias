import { useEffect, useState } from "react";
import { api, type Transfer } from "../api";
import { Drawer } from "../components/Drawer";
import { Receipt } from "../components/Receipt";

/** Comprovante aberto a partir de uma linha do extrato (GET /transfers/{id}). */
export function TransferDetails({ transferId, onClose }: { transferId: string; onClose: () => void }) {
  const [transfer, setTransfer] = useState<Transfer | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.transfer(transferId).then(setTransfer).catch((e: Error) => setError(e.message));
  }, [transferId]);

  return (
    <Drawer title="Comprovante" onClose={onClose}>
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!transfer && !error && <div className="skeleton skeleton--receipt" />}
      {transfer && <Receipt transfer={transfer} />}
    </Drawer>
  );
}
