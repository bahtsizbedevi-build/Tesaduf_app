// { match_id, body, client_id } -> message. client_id makes retries idempotent.
// After storing, the recipient gets an FCM push (in the background, never blocking).
import { requireString, requireUuid, serve } from "../_shared/api.ts";
import { pushNewMessage } from "../_shared/push.ts";

declare const EdgeRuntime: { waitUntil(p: Promise<unknown>): void } | undefined;

serve("send-message", async ({ body, rpc }) => {
  const message = await rpc<{ id: number }>("send_message", {
    p_match_id: requireUuid(body, "match_id"),
    p_body: requireString(body, "body", 1000, "INVALID_MESSAGE"),
    p_client_id: requireUuid(body, "client_id", "INVALID_CLIENT_ID"),
  });
  const push = pushNewMessage(message.id);
  if (typeof EdgeRuntime !== "undefined") EdgeRuntime.waitUntil(push); else await push;
  return message;
});
