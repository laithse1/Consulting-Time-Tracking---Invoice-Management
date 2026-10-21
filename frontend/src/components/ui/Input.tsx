export function Input({
  label,
  name,
  value,
  type = "text",
  required = false,
  ...rest
}: {
  label: string;
  name: string;
  value?: string | number | null;
  type?: string;
  required?: boolean;
  min?: string;
  max?: string;
  step?: string;
}) {
  return (
    <label className="field">
      {label}
      <input name={name} type={type} defaultValue={value ?? ""} required={required} {...rest} />
    </label>
  );
}
