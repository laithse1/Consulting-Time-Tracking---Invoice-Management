import { useWorkspace } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { applicationConfig } from "@/config/application";
import { formData } from "@/lib/forms";
import { ArrowUpRight } from "lucide-react";
export function UnlockWorkspace() {
  const { setToken, error } = useWorkspace();
  return (
    <div className="login">
      <div className="brandmark">
        ci<span>m</span>
      </div>
      <h1>{applicationConfig.title}</h1>
      <p className="muted">Your work, accounted for.</p>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          setToken(String(b.token));
        }}
      >
        <Input label="Local access token" name="token" type="password" required />
        <Button type="submit">
          Unlock workspace <ArrowUpRight size={16} />
        </Button>
      </form>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <p className="muted small">Use APP_TOKEN from your local configuration.</p>
    </div>
  );
}
