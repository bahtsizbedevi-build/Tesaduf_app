// { avatar: "av_<color 1-8>_<accessory 1-9>" } -> profile. Only the accessory can change; colour and anonymous id are fixed.
import { ApiError, serve } from "../_shared/api.ts";

serve("update-profile", ({ body, rpc }) => {
  const avatar = body.avatar;
  if (typeof avatar !== "string" || !/^av_[1-8]_[1-9]$/.test(avatar)) throw new ApiError(400, "INVALID_AVATAR");
  return rpc("update_avatar", { p_avatar: avatar });
});
