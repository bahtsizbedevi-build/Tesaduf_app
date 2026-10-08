// Moderation (admins only, enforced in the database):
// { action: "reports", status?: "open" | "reviewed" | "dismissed" } -> reports with evidence
// { action: "resolve", report_id, decision: "suspend" | "dismiss" } -> { ok }
import { ApiError, optionalInt, requireEnum, serve } from "../_shared/api.ts";

serve("admin", ({ body, rpc }) => {
  const action = requireEnum(body, "action", ["reports", "resolve"] as const, "INVALID_ACTION");
  if (action === "reports") {
    const status = body.status === undefined ? "open" : requireEnum(body, "status", ["open", "reviewed", "dismissed"] as const, "INVALID_STATUS");
    return rpc("admin_reports", { p_status: status });
  }
  const reportId = optionalInt(body, "report_id", 1, Number.MAX_SAFE_INTEGER, 0);
  if (reportId === 0) throw new ApiError(400, "INVALID_ACTION");
  const decision = requireEnum(body, "decision", ["suspend", "dismiss"] as const, "INVALID_ACTION");
  return rpc("admin_resolve", { p_report_id: reportId, p_action: decision });
});
