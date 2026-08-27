# Eval Report 9491f6ca-675d-4e26-9df9-2ea670aadf4b

- started: 2026-08-23T08:25:55.766912400Z
- finished: 2026-08-23T08:28:19.080973300Z
- result: **12/13 passed**

| Scenario | Verdict (exp -> act) | Confidence | needsHuman | Tools | Result |
|---|---|---|---|---|---|
| demo-app-down-honest-logs | any -> INCONCLUSIVE | 0.00 | true | fetch_runbook,get_alert_detail,get_topology,query_metric,search_logs,get_log_labels,search_kb | ✅ PASS |
| demo-app-down | CONFIRMED -> CONFIRMED | 0.90 | true | fetch_runbook,get_alert_detail,get_topology,query_metric,search_logs,get_log_labels,search_kb | ✅ PASS |
| hlg-03-port-forward-command | any -> INCONCLUSIVE | 0.00 | true |  | ✅ PASS |
| hlg-100a-loki-connection-pool | any -> INCONCLUSIVE | 0.00 | true | get_topology,get_alert_detail,get_deploy_window,get_log_labels,search_logs,search_kb | ❌ FAIL |
| hlg-102-loki-label-discovery | any -> INCONCLUSIVE | 0.50 | true | get_log_labels,get_topology,search_logs | ✅ PASS |
| hlg-103-logs-transparency-limit | any -> INCONCLUSIVE | 0.00 | true | get_log_labels,search_logs | ✅ PASS |
| hlg-16-no-toolset-transparency | any -> INCONCLUSIVE | 0.00 | true | get_alert_detail,get_topology,fetch_runbook,get_deploy_window,query_metric,get_log_labels,search_logs,search_kb | ✅ PASS |
| hlg-23-dns-resolution-error | any -> INCONCLUSIVE | 0.00 | true | get_topology,get_alert_detail,get_deploy_window,fetch_runbook,get_log_labels,search_logs,query_metric,search_kb | ✅ PASS |
| hlg-256-hello-basic | any -> INCONCLUSIVE | 0.00 | true |  | ✅ PASS |
| hlg-50a-logs-honest-empty | any -> INCONCLUSIVE | 0.90 | true | get_log_labels,get_topology,search_logs | ✅ PASS |
| hlg-97-logs-clarification | any -> INCONCLUSIVE | 0.00 | true | get_topology,get_log_labels | ✅ PASS |
| hlg-99-logs-transparency-custom-time | any -> INCONCLUSIVE | 0.00 | true | get_log_labels,search_logs | ✅ PASS |
| unknown-service | any -> LIKELY_FALSE_POSITIVE | 0.90 | true | get_alert_detail,get_topology,fetch_runbook,get_log_labels,query_metric,search_logs,search_kb | ✅ PASS |

## Failures

### hlg-100a-loki-connection-pool
- report must contain "ConnectionPoolExhausted"
