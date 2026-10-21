import { ChevronRight } from "lucide-react";
export function Step({
  number,
  title,
  text,
  onClick,
}: {
  number: string;
  title: string;
  text: string;
  onClick: () => void;
}) {
  return (
    <button className="step" onClick={onClick}>
      <span>{number}</span>
      <div>
        <strong>{title}</strong>
        <p className="muted small">{text}</p>
      </div>
      <ChevronRight size={16} />
    </button>
  );
}
