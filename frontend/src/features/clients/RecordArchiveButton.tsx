import { useWorkspaceData } from "@/app/WorkspaceContext";
import { request } from "@/lib/api/client";
import { Button } from "@/components/ui/Button";

export function RecordArchiveButton({
  type,
  record,
}: {
  type: "clients" | "contracts" | "projects";
  record: { id: number; archived?: boolean; archived_self?: boolean };
}) {
  const { act } = useWorkspaceData();
  if (record.archived && !record.archived_self)
    return (
      <p className="small muted">Client archived · restore the client to activate this record.</p>
    );
  const restore = !!record.archived_self;
  const label = `${restore ? "Restore" : "Archive"} ${type.slice(0, -1)}`;
  return (
    <Button
      secondary
      onClick={() => {
        if (confirm(`${label}? Existing work, invoices, and payment history will be preserved.`))
          act(
            () =>
              request(`/records/${type}/${record.id}/archive`, {
                archived: !restore,
                confirmed: true,
              }),
            restore ? "Record restored" : "Record archived",
          );
      }}
    >
      {label}
    </Button>
  );
}
