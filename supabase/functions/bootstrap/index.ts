// Creates (first call) or loads the caller's anonymous profile, plus any live match to resume.
import { serve } from "../_shared/api.ts";

serve("bootstrap", ({ rpc }) => rpc("ensure_profile"));
