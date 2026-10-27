import { useContext, type ReactNode } from "react";
import { BusyContext } from "./BusyContext";
export function Button({
  children,
  onClick,
  secondary = false,
  type = "button",
  disabled = false,
}: {
  children: ReactNode;
  onClick?: () => void;
  secondary?: boolean;
  type?: "button" | "submit";
  disabled?: boolean;
}) {
  const busy = useContext(BusyContext);
  return (
    <button
      type={type}
      disabled={busy || disabled}
      className={secondary ? "btn secondary" : "btn"}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
