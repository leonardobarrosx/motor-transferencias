import { useEffect, useRef, type ReactNode } from "react";
import { Icon } from "./Icon";

export function Drawer({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const closeButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    closeButton.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => { window.removeEventListener("keydown", onKey); previous?.focus(); };
  }, [onClose]);

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} aria-hidden="true" />
      <div className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
        <div className="drawer__head">
          <h2 id="drawer-title" className="drawer__title">{title}</h2>
          <button ref={closeButton} className="icon-btn" type="button" onClick={onClose} aria-label="Fechar"><Icon name="close" /></button>
        </div>
        <div className="drawer__body">{children}</div>
      </div>
    </>
  );
}
