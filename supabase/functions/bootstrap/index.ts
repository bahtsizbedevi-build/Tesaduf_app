// Creates (first call) or loads the caller's anonymous profile, stats, live tesadüfs,
// and whether the caller is a moderator.
import { serve } from "../_shared/api.ts";

serve("bootstrap", async ({ rpc }) => {
  const profile = await rpc<Record<string, unknown>>("ensure_profile");
  const isAdmin = await rpc<boolean>("is_admin");
  return { ...profile, is_admin: isAdmin === true };
});
