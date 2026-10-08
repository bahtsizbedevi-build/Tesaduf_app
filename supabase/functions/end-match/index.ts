// { match_id } -> match (status "ended").
import { requireUuid, serve } from "../_shared/api.ts";

serve("end-match", ({ body, rpc }) => rpc("end_match", { p_match_id: requireUuid(body, "match_id") }));
