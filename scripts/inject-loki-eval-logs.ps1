# inject-loki-eval-logs.ps1
# Pushes synthetic log data into the local Loki instance for the HolmesGPT-derived
# log eval scenarios (hlg-100a / hlg-99 / hlg-102 / hlg-103 / hlg-23).
# hlg-50a (ghost-pod) intentionally gets NO logs.
#
# IMPORTANT: all timestamps are kept within the last ~5-12 minutes — TWO reasons:
# 1) Loki rejects "too far behind" entries (see grafana/loki issues #18669/#5936 —
#    the reject_old_samples_max_age limit is unreliable in 3.x);
# 2) the agent's search_logs default window is the last 15 minutes — older logs
#    are invisible to the first (and often only) query, which failed the eval.
# Re-run this script immediately before an eval run so the data is fresh.
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts/inject-loki-eval-logs.ps1
# Prereq: docker compose up -d   (Loki on localhost:3100)

param(
    [string]$LokiUrl = "http://localhost:3100"
)

$ErrorActionPreference = "Stop"

function Get-TsNs([long]$millisAgo) {
    $ms = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $millisAgo
    return [string]($ms * 1000000)
}

# Builds one Loki stream: values sorted ascending, one line every $spacingMs.
function New-LokiStream([hashtable]$labels, [string[]]$lines, [long]$startMillisAgo, [long]$spacingMs) {
    $values = @()
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $values += , @((Get-TsNs ($startMillisAgo + $i * $spacingMs)), $lines[$i])
    }
    return @{ stream = $labels; values = $values }
}

function Push-Streams([string]$name, [object[]]$streams) {
    $body = @{ streams = @($streams) } | ConvertTo-Json -Depth 12
    try {
        Invoke-RestMethod -Uri "$LokiUrl/loki/api/v1/push" -Method Post `
            -ContentType "application/json" -Body $body | Out-Null
        Write-Host "[$name] pushed OK"
    } catch {
        Write-Host "[$name] WARNING: push failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "Injecting eval logs into Loki at $LokiUrl ..."

# --- hlg-100a: payment-api connection pool exhaustion ---
$paymentApi = New-LokiStream @{ job = "eval"; pod = "payment-api" } @(
    "INFO  payment-api started, connecting to database",
    "INFO  health check /healthz OK - acquired connection from pool",
    "WARN  health check /healthz FAILED - failed to acquire database connection",
    "ERROR Failed to acquire database connection - pool exhausted (ConnectionPoolExhausted)",
    "ERROR Failed to acquire database connection - pool exhausted (ConnectionPoolExhausted)",
    "WARN  connection pool exhausted, 40 active / 40 max connections",
    "ERROR Failed to acquire database connection - pool exhausted (ConnectionPoolExhausted)",
    "ERROR request /api/orders failed: database connection unavailable",
    "WARN  retrying request /api/orders in 1s",
    "ERROR Failed to acquire database connection - pool exhausted (ConnectionPoolExhausted)",
    "INFO  health check /healthz FAILED - connection pool exhausted",
    "ERROR connection pool exhausted after 5 consecutive failures",
    # cascade scenario (Phase 3): payment-api depends on db-primary
    "ERROR POST /orders 503 - upstream timeout calling db-primary",
    "WARN  retrying POST /orders - db-primary still slow, 5s elapsed",
    "ERROR GET /orders 504 - gateway timeout after waiting on db-primary"
) 300000 20000
Push-Streams "hlg-100a / cascade" $paymentApi

# --- cascade (Phase 3): db-primary slow queries (dependency of payment-api) ---
$dbPrimary = New-LokiStream @{ job = "eval"; pod = "db-primary" } @(
    "INFO  db-primary accepting connections",
    "WARN  slow query: SELECT * FROM orders WHERE user_id=? took 8.2s",
    "WARN  slow query: UPDATE orders SET status=? took 7.1s",
    "ERROR query execution timeout after 10s: SELECT * FROM orders",
    "WARN  connection wait timeout - 40 active connections",
    "ERROR query execution timeout after 10s: SELECT * FROM payments",
    "WARN  slow query: SELECT * FROM payments WHERE order_id=? took 9.5s",
    "INFO  db-primary recovered, query times back to 40ms"
) 300000 30000
Push-Streams "cascade" $dbPrimary

# --- hlg-99: time-traveler logs spread within the last ~55 min ---
$timeTraveler = New-LokiStream @{ job = "eval"; pod = "time-traveler" } @(
    "INFO  time-traveler started",
    "INFO  scheduled job completed in 120ms",
    "WARN  cache hit ratio below threshold: 0.82",
    "ERROR request /time failed: upstream timeout after 30s",
    "INFO  retry succeeded after 2 attempts",
    "WARN  memory usage at 78% of limit",
    "INFO  scheduled job completed in 95ms",
    "ERROR request /time failed: upstream timeout after 30s",
    "INFO  recovery: upstream back to normal",
    "INFO  time-traveler healthy"
) 300000 30000
Push-Streams "hlg-99" $timeTraveler

# --- hlg-102: inventory-service with NON-STANDARD label pod_name (and namespace
# --- matching the scenario prompt so {namespace="app-102"} also resolves) ---
$inventory = New-LokiStream @{ job = "eval"; namespace = "app-102"; pod_name = "inventory-service-7c9d4f" } @(
    "Inventory item ITEM-1234 checked - stock level: 45 units",
    "Order ORD-5678 processed - 3 units of ITEM-1234",
    "Stock updated for ITEM-1234 - new level: 42 units",
    "Low stock warning for ITEM-9999 - current level: 5 units",
    "Inventory reconciliation completed - 127 items verified"
) 300000 30000
Push-Streams "hlg-102" $inventory

# --- hlg-103: deep-diver, 150 mixed-level lines within the last ~55 min ---
$deepDiverLines = @()
for ($i = 0; $i -lt 150; $i++) {
    switch ($i % 5) {
        0 { $deepDiverLines += "INFO  deep-diver processing item $i" }
        1 { $deepDiverLines += "WARN  deep-diver slow operation detected: $($i * 37)ms" }
        2 { $deepDiverLines += "ERROR deep-diver operation failed: timeout (attempt $i)" }
        3 { $deepDiverLines += "INFO  deep-diver retrying operation $i" }
        4 { $deepDiverLines += "WARN  deep-diver queue depth above threshold: $i" }
    }
}
$deepDiver = New-LokiStream @{ job = "eval"; pod = "deep-diver" } $deepDiverLines 300000 3000
Push-Streams "hlg-103" $deepDiver

# --- hlg-23: meme-deployment DNS resolution errors ---
$meme = New-LokiStream @{ job = "eval"; pod = "meme-deployment" } @(
    "WARN  curl request failed: Failed to reach memcom - no such host (DNS resolution failed)",
    "ERROR request to http://memcom/api failed: Failed to reach memcom - no such host",
    "ERROR Failed to reach memcom: hostname could not be resolved",
    "WARN  retrying http://memcom ... Failed to reach memcom",
    "ERROR curl: (6) Could not resolve host: memcom",
    "WARN  connection attempt to memcom timed out"
) 300000 30000
Push-Streams "hlg-23" $meme

# --- hlg-50a: ghost-pod intentionally NOT injected (expects honest "no logs") ---
Write-Host "[hlg-50a] ghost-pod intentionally left without logs"

# --- self-verify: Loki flushes chunks ~20s after push (chunk_idle_period=15s),
# --- but the TSDB index sync can delay visibility by SEVERAL minutes (observed:
# --- labels + log queries returning empty mid-eval right after injection).
# --- So POLL until BOTH selectors ({job="eval"} and {pod="payment-api"}) return
# --- data via the same query path the agent uses, then declare ready. ---
Write-Host "Polling Loki until the injected data is queryable (max 4 minutes)..."
$deadline = [DateTimeOffset]::UtcNow.AddMinutes(4)
$verified = $false
while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $verifyNowNs = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000).ToString()
    $verifyStartNs = ([DateTimeOffset]::UtcNow.AddMinutes(-30).ToUnixTimeMilliseconds() * 1000000).ToString()
    try {
        $resp = Invoke-RestMethod -Uri "$LokiUrl/loki/api/v1/query_range?query=%7Bjob%3D%22eval%22%7D&limit=5&start=$verifyStartNs&end=$verifyNowNs" -TimeoutSec 15
        $streams = $resp.data.result.Count
        $resp2 = Invoke-RestMethod -Uri "$LokiUrl/loki/api/v1/query_range?query=%7Bpod%3D%22payment-api%22%7D&limit=5&start=$verifyStartNs&end=$verifyNowNs" -TimeoutSec 15
        $paymentLines = $resp2.data.result.values.Count
        if ($streams -ge 5 -and $paymentLines -ge 1) {
            $verified = $true
            break
        }
        Write-Host "  ... $streams streams / $paymentLines payment lines — waiting for index sync..."
    } catch {
        Write-Host "  ... verify query failed: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 20
}
if ($verified) {
    Write-Host "VERIFY OK: $streams streams queryable (pod selector: $paymentLines lines) — ready for eval" -ForegroundColor Green
} else {
    Write-Host "WARNING: data still not queryable after 4 minutes — check Loki logs before running the eval" -ForegroundColor Yellow
}

Write-Host "Done. You can now run: POST /api/evals/run?scenario=hlg-100a (etc.)"
