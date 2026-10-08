// { match_id? } -> presence ping; returns the match when match_id is given.
import { optionalUuid, serve } from "../_shared/api.ts";

serve("heartbeat", ({ body, rpc }) => rpc("heartbeat", { p_match_id: optionalUuid(body, "match_id") }));
