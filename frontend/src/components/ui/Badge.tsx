import type { ReactNode } from "react";
export function Badge({ children }: { children: ReactNode }) {
  return (
    <span
      className={
        "badge " +
        (String(children).match(/Paid|Within|Approved/)
          ? "green"
          : String(children).match(/Overdue|Disputed|required/)
            ? "red"
            : "")
      }
    >
      {children}
    </span>
  );
}
