// { match_id, decision: "continue" | "end" } -> match. Atomic on the database side.
import { requireEnum, requireUuid, serve } from "../_shared/api.ts";

serve("destiny-decision", ({ body, rpc }) =>
  rpc("submit_decision", {
    p_match_id: requireUuid(body, "match_id"),
    p_decision: requireEnum(body, "decision", ["continue", "end"] as const, "INVALID_DECISION"),
  }));
