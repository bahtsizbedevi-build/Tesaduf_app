// { match_id, after?: number, limit?: number } -> { match, messages }
// after = 0 returns the latest `limit` messages; otherwise only messages with id > after.
import { optionalInt, requireUuid, serve } from "../_shared/api.ts";

serve("messages", ({ body, rpc }) =>
  rpc("list_messages", {
    p_match_id: requireUuid(body, "match_id"),
    p_after: optionalInt(body, "after", 0, Number.MAX_SAFE_INTEGER, 0),
    p_limit: optionalInt(body, "limit", 1, 200, 100),
  }));
