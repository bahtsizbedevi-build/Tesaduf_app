// { match_id } -> match (time based transitions are applied server side).
import { requireUuid, serve } from "../_shared/api.ts";

serve("match-status", ({ body, rpc }) => rpc("get_match", { p_match_id: requireUuid(body, "match_id") }));
