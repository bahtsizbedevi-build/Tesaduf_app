// { match_id } -> blocks the partner of that match and ends it. The partner's
// internal id is never sent to clients, so blocking is done by match.
import { requireUuid, serve } from "../_shared/api.ts";

serve("block-user", ({ body, rpc }) => rpc("block_user", { p_match_id: requireUuid(body, "match_id") }));
