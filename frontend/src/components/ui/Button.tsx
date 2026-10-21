import { useContext, type ReactNode } from "react";
import { BusyContext } from "./BusyContext";
export function Button({
  children,
  onClick,
  secondary = false,
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  secondary?: boolean;
  type?: "button" | "submit";
}) {
  return (
    <button
      type={type}
      disabled={useContext(BusyContext)}
      className={secondary ? "btn secondary" : "btn"}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
