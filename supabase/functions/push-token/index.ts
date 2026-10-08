// { action: "register" | "unregister", token } -> { ok }
import { requireEnum, requireString, serve } from "../_shared/api.ts";

serve("push-token", ({ body, rpc }) => {
  const action = requireEnum(body, "action", ["register", "unregister"] as const, "INVALID_ACTION");
  const token = requireString(body, "token", 4096, "INVALID_TOKEN");
  return rpc(action === "register" ? "register_push_token" : "unregister_push_token", { p_token: token });
});
