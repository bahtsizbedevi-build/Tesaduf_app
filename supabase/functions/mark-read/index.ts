// { match_id, last_id } -> { last_read }. Read receipts only move forward.
import { optionalInt, requireUuid, serve } from "../_shared/api.ts";

serve("mark-read", ({ body, rpc }) =>
  rpc("mark_read", {
    p_match_id: requireUuid(body, "match_id"),
    p_last_id: optionalInt(body, "last_id", 0, Number.MAX_SAFE_INTEGER, 0),
  }));
