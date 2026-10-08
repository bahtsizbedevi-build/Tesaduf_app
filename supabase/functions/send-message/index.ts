// { match_id, body, client_id } -> message. client_id makes retries idempotent.
import { requireString, requireUuid, serve } from "../_shared/api.ts";

serve("send-message", ({ body, rpc }) =>
  rpc("send_message", {
    p_match_id: requireUuid(body, "match_id"),
    p_body: requireString(body, "body", 1000, "INVALID_MESSAGE"),
    p_client_id: requireUuid(body, "client_id", "INVALID_CLIENT_ID"),
  }));
