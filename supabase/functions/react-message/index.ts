// { message_id, reaction: "heart" | "laugh" | "wow" | "sad" | "fire" | null } -> message.
// Only the recipient of a message can react.
import { ApiError, optionalInt, serve } from "../_shared/api.ts";

const reactions = ["heart", "laugh", "wow", "sad", "fire"];

serve("react-message", ({ body, rpc }) => {
  const messageId = optionalInt(body, "message_id", 1, Number.MAX_SAFE_INTEGER, 0);
  if (messageId === 0) throw new ApiError(400, "INVALID_REACTION");
  const reaction = body.reaction ?? null;
  if (reaction !== null && (typeof reaction !== "string" || !reactions.includes(reaction))) {
    throw new ApiError(400, "INVALID_REACTION");
  }
  return rpc("react_message", { p_message_id: messageId, p_reaction: reaction });
});
