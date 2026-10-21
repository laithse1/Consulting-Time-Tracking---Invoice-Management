import { createContext, useContext, type ReactNode } from "react";
import { useWorkspaceController } from "../hooks/useWorkspaceController";

type WorkspaceState = ReturnType<typeof useWorkspaceController>;
const WorkspaceContext = createContext<WorkspaceState | null>(null);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const workspace = useWorkspaceController();
  return <WorkspaceContext.Provider value={workspace}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const workspace = useContext(WorkspaceContext);
  if (!workspace) throw new Error("WorkspaceProvider is required");
  return workspace;
}

/** Feature screens mount only after the workspace is unlocked and loaded. */
export function useWorkspaceData() {
  const workspace = useWorkspace();
  if (!workspace.data) throw new Error("Workspace data has not loaded");
  return { ...workspace, data: workspace.data };
}
