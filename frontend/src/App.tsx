import { WorkspaceProvider } from "./app/WorkspaceContext";
import { WorkspaceShell } from "./app/WorkspaceShell";
export { Badge } from "./components/ui/Badge";

export default function App() {
  return (
    <WorkspaceProvider>
      <WorkspaceShell />
    </WorkspaceProvider>
  );
}
