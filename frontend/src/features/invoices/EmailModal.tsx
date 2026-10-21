import { useState, useEffect } from "react";
import { request } from "../../api";
import { formData } from "../../lib/forms";
import { Modal } from "../../components/ui/Modal";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
export function EmailModal({ id, onClose }: { id: number; onClose: () => void }) {
  const [mail, setMail] = useState<{
    to: string;
    subject: string;
    body: string;
  }>();
  const [copied, setCopied] = useState(false);
  const [err, setErr] = useState("");
  useEffect(() => {
    request<{ to: string; subject: string; body: string }>(`/invoices/${id}/email`)
      .then(setMail)
      .catch((e) => setErr(e.message));
  }, [id]);
  return (
    <Modal title="Prepare submission email" onClose={onClose}>
      {err && <p role="alert">{err}</p>}
      {mail && (
        <form
          onSubmit={(event) => {
            const values = formData(event);
            const text = `To: ${values.to}\n${values.cc ? `CC: ${values.cc}\n` : ""}Subject: ${values.subject}\n\n${values.body}`;
            navigator.clipboard
              .writeText(text)
              .then(() => setCopied(true))
              .catch(() => setErr("Clipboard unavailable. Select and copy the text manually."));
          }}
        >
          <Input label="To" name="to" value={mail.to} />
          <Input label="Optional CC to Brandon (enter address)" name="cc" type="email" />
          <Input label="Subject" name="subject" value={mail.subject} />
          <textarea
            aria-label="Email body"
            name="body"
            className="email-body"
            defaultValue={mail.body}
          />
          <Button type="submit">{copied ? "Copied" : "Copy email subject & body"}</Button>
          <p className="muted small">
            Attach the downloaded PDF in your email app. Nothing is sent automatically.
          </p>
        </form>
      )}
    </Modal>
  );
}
