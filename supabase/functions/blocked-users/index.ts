// { action: "list" } -> blocked users (anonymous id + avatar only)
// { action: "unblock", block_id } -> { unblocked }
import { ApiError, optionalInt, requireEnum, serve } from "../_shared/api.ts";

serve("blocked-users", ({ body, rpc }) => {
  const action = requireEnum(body, "action", ["list", "unblock"] as const, "INVALID_ACTION");
  if (action === "list") return rpc("list_blocks");
  const blockId = optionalInt(body, "block_id", 1, Number.MAX_SAFE_INTEGER, 0);
  if (blockId === 0) throw new ApiError(400, "INVALID_BLOCK_ID");
  return rpc("unblock", { p_block_id: blockId });
});
