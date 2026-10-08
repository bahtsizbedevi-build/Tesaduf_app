// { action: "join" | "cancel", mode?: "text" }
// join: returns { state: "matched", match } or { state: "waiting", ... }. Clients poll join
// every few seconds; each call also keeps the queue entry fresh.
import { requireEnum, serve } from "../_shared/api.ts";

serve("matchmaker", ({ body, rpc }) => {
  const action = requireEnum(body, "action", ["join", "cancel"] as const, "INVALID_ACTION");
  if (action === "cancel") return rpc("cancel_matchmaking");
  const mode = body.mode === undefined ? "text" : requireEnum(body, "mode", ["text", "voice"] as const, "INVALID_MODE");
  return rpc("find_match", { p_mode: mode });
});
