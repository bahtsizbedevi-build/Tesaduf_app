// { match_id, reason, details? } -> stores a report, blocks the partner and ends the match.
import { optionalString, requireEnum, requireUuid, serve } from "../_shared/api.ts";

const reasons = ["spam", "insult", "harassment", "inappropriate", "other"] as const;

serve("report-user", ({ body, rpc }) =>
  rpc("report_user", {
    p_match_id: requireUuid(body, "match_id"),
    p_reason: requireEnum(body, "reason", reasons, "INVALID_REASON"),
    p_details: optionalString(body, "details", 500, "INVALID_DETAILS"),
  }));
