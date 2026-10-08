// { limit? } -> past tesadüfs (anonymous partner id, dates, status, destiny flag).
import { optionalInt, serve } from "../_shared/api.ts";

serve("my-chats", ({ body, rpc }) => rpc("my_chats", { p_limit: optionalInt(body, "limit", 1, 100, 30) }));
