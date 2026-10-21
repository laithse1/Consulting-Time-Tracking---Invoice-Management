import type { FormEvent } from "react";

export type FormFields = Record<string, string | number | null | undefined>;

export function formData(event: FormEvent<HTMLFormElement>): FormFields {
  event.preventDefault();
  return Object.fromEntries(
    [...new FormData(event.currentTarget).entries()].map(([key, value]) => [key, String(value)]),
  );
}
