import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AssistantConversation } from "./AssistantConversation";
import type { Plan } from "./types";

const plan: Plan = {
  id: "fixture",
  source: "Today 2h testing Audiom",
  status: "proposed",
  version: 1,
  created: "2026-09-15T10:00:00Z",
  engine: "local-rules",
  actions: [],
  results: [],
};
afterEach(cleanup);

it("requires consent for sending the conversation and clears only successful corrections", async () => {
  const revise = vi.fn().mockResolvedValue(true);
  render(
    <AssistantConversation
      plan={plan}
      provider="fixture"
      busy={false}
      onRevise={revise}
      onReopen={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("Correction message"), {
    target: { value: "Make task 1 concise" },
  });
  const send = screen.getByRole("button", { name: "Propose correction" });
  expect(send).toBeDisabled();
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.click(send);
  await waitFor(() => expect(revise).toHaveBeenCalledWith("Make task 1 concise", true));
  await waitFor(() => expect(screen.getByLabelText("Correction message")).toHaveValue(""));
  expect(screen.getByRole("checkbox")).not.toBeChecked();
});

it("retains a failed correction through plan version updates", async () => {
  const revise = vi.fn().mockResolvedValue(false);
  const props = { provider: "", busy: false, onRevise: revise, onReopen: vi.fn() };
  const { rerender } = render(<AssistantConversation plan={plan} {...props} />);
  fireEvent.change(screen.getByLabelText("Correction message"), {
    target: { value: "Task 1 duration: 90m" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Propose correction" }));
  await waitFor(() => expect(revise).toHaveBeenCalled());
  rerender(<AssistantConversation plan={{ ...plan, version: 2 }} {...props} />);
  expect(screen.getByLabelText("Correction message")).toHaveValue("Task 1 duration: 90m");
});

it("reopens saved drafts explicitly and displays persisted conversation replies", () => {
  const reopen = vi.fn();
  render(
    <AssistantConversation
      plan={{
        ...plan,
        status: "applied",
        revisions: [
          {
            version: 2,
            instruction: "Task 1 duration: 90m",
            reply: "Review the corrected duration.",
            engine: "local-rules",
            created: plan.created,
          },
        ],
      }}
      provider=""
      busy={false}
      onRevise={vi.fn()}
      onReopen={reopen}
    />,
  );
  expect(screen.getByText("Review the corrected duration.")).toBeInTheDocument();
  expect(screen.queryByLabelText("Correction message")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Revise saved draft" }));
  expect(reopen).toHaveBeenCalledOnce();
});
