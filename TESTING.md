# 测试指南（Testing Guide）

本项目按 HolmesGPT 的"三层测试"思路组织：**单元测试 → 构建验证 → 端到端冒烟**（LLM 评测属 Phase 2 计划）。

## 1. 测试分层与覆盖

| 层级 | 位置 | 覆盖内容 | 需要基础设施 |
|---|---|---|---|
| 单元测试 | `src/test/java/` | 工具执行、告警去重、Case 状态机、提示词构建、Verdict 解析、Loki 响应格式化、反馈/接入逻辑 | ❌ 无（Mock + H2 + MockRestServiceServer） |
| 构建验证 | `mvnw package` | 编译全部源码 + 跑全部测试 + 打包可执行 jar | ❌ 无 |
| 端到端冒烟 | 手动 curl + docker compose | 告警接入 → 去重 → 调查 → 报告 → 反馈 全链路 | Docker（LLM Key 可选） |
| LLM 评测 | （Phase 2 计划） | 真实 LLM 调查质量回归、防幻觉用例 | LLM Key + 可观测底座 |

当前共有 **8 个测试类、20 个测试方法**，全部可在无外部服务的情况下运行。

## 2. 如何运行

### 2.1 只跑单元测试（最快反馈，约 30 秒）

```bash
.\mvnw.cmd test
```

指定测试类 / 方法：

```bash
.\mvnw.cmd test -Dtest=CaseStateMachineTest          # 单个类
.\mvnw.cmd test -Dtest=CaseStateMachineTest#confirmedVerdictCompletesAsReported   # 单个方法
.\mvnw.cmd test "-Dtest=*ServiceTest"                # 通配符
```

### 2.2 完整构建（编译 + 测试 + 打包）

```bash
.\mvnw.cmd package
```

产物：`target/ai-ops-agent-0.1.0.jar`（可直接 `java -jar` 运行）。

### 2.3 端到端冒烟（验证真实链路）

```bash
# 1. 启动可观测性底座（Prometheus/AlertManager/Loki/Postgres/Redis/Grafana）
docker compose up -d

# 2. 启动应用（未配置 DEEPSEEK_API_KEY 时自动进入"影子模式"，不调用 LLM）
java -jar target\ai-ops-agent-0.1.0.jar

# 3. 手动触发一条告警
curl -X POST http://localhost:8080/api/alerts/simple -H "Content-Type: application/json" -d "{
  \"alertName\":\"DemoAppDown\",\"status\":\"firing\",
  \"labels\":{\"service\":\"demo-app\"},
  \"annotations\":{\"summary\":\"demo app is down\"},
  \"startsAt\":\"2025-01-01T10:00:00Z\"}"

# 4. 查看调查结果
curl http://localhost:8080/api/cases
curl http://localhost:8080/api/cases/<caseId>

# 5. 模拟人工反馈
curl -X POST http://localhost:8080/api/cases/<caseId>/feedback -H "Content-Type: application/json" -d '{"outcome":"FALSE_POSITIVE","comment":"部署抖动"}'
curl http://localhost:8080/api/feedback/stats

# 6. 等 1~2 分钟，观察 AlertManager 真实推送（演示规则 DemoAppDown 会自动触发并 POST 到本应用）
curl http://localhost:8080/api/cases   # 应出现第二条自动创建的 Case
```

## 3. 测试结果的含义

### 3.1 单元测试输出

```
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 字段 | 含义 | 出现非 0 时说明 |
|---|---|---|
| `Tests run` | 本次执行的测试方法总数 | 少于预期 = 有测试没被收集（类名/方法名规范问题） |
| `Failures` | **断言失败**数：结果与预期不符 | 代码行为变了，或测试断言过时——先看是哪个变了 |
| `Errors` | **异常**数：测试执行中抛了未捕获异常 | 代码崩溃 / 依赖注入失败 / 空指针等 |
| `Skipped` | 跳过的测试数 | `@Disabled` 或前置条件（如 assumption）不满足 |
| `BUILD SUCCESS / FAILURE` | 整个 Maven 生命周期是否通过 | FAILURE 时看具体 ERROR 行与 `target/surefire-reports/` |

失败详情在 `target/surefire-reports/` 目录（`*.txt` 人类可读、`*.xml` 给 CI 解析）。

### 3.2 端到端冒烟的数字含义

| 输出 | 含义 |
|---|---|
| `{"accepted":1,"duplicates":0}` | 收到 1 条**新**告警，已创建 Case 并触发调查 |
| `{"accepted":0,"duplicates":1}` | 同一条告警在去重窗口（默认 60 分钟）内重复到达，被忽略 |
| Case `status` | `NEW`→`INVESTIGATING`→`REPORTED`（确认故障）/`ESCALATED`（需人工）/`FALSE_POSITIVE`（疑似误报）/`ERROR` |
| `verdict` | `CONFIRMED` 真故障 / `LIKELY_FALSE_POSITIVE` 证据矛盾指向误报 / `INCONCLUSIVE` 无法确定 |
| `confidence` | 0~1 的置信度（LLM 自评） |
| `needsHuman` | 是否建议人工接管（低置信度 / 高影响 / 需要写操作） |
| `DemoAppDown: [1, 1, 0]` | 该告警规则：**总数 1、被标误报 1、被标真故障 0**——误报率报表的数据源 |

> **影子模式下的预期结果**：未配置 LLM Key 时，调查会跳过 LLM 并产出占位裁决
> `INCONCLUSIVE / confidence=0 / needsHuman=true / status=ESCALATED`。
> 这是**预期行为**（安全设计：无模型时绝不假装查出了根因），不是测试失败。

### 3.3 配置 LLM Key 后的真实裁决

配置 `DEEPSEEK_API_KEY` 后，调查会真正调用 DeepSeek 走 agentic loop。结果判读：

- `CONFIRMED` + 置信度 ≥ 0.6 + 无需人工 → `REPORTED`：定位到了根因，附证据链可审计；
- `INCONCLUSIVE` 或置信度 < 0.6 → `ESCALATED`：证据不足，转人工（系统会给出升级原因）；
- `LIKELY_FALSE_POSITIVE` → `FALSE_POSITIVE`：证据与告警矛盾（如"告警说 CPU 高但指标空闲"），等人工确认。

## 4. Eval 评测（LLM 回归，Phase 2）

> Eval 与单元测试的本质区别：单元测试是**确定性**的（同一代码永远同一结果）；Eval 走**真实调查管道 + 真实 LLM**，
> 同场景多次运行结果可能波动——所以验收标准是"**稳定基线**"（跑 N 次统计通过率），而不是"每次都 100%"。

### 4.1 流程（从触发到报告）

```
POST /api/evals/run?scenario=all
  → EvalRunner 生成 runId，为每个场景构造唯一告警（fingerprint=eval-<场景id>-<runId>，绕过去重）
  → 走真实管道：AlertIntakeService → 创建 Case → InvestigationRunner 异步调查（LLM agentic loop）
  → EvalRunner 每 2 秒轮询 Case 状态（最长等 6 分钟）
  → 调查结束：读取 verdict / confidence / needsHuman / 证据链 / 报告
  → EvalEvaluator 逐条断言 → failures 列表 → EvalResult
  → 汇总为 EvalRun，写入 evals/report-<runId>.md + API 可查
```

API：`GET /api/evals/scenarios` ｜ `POST /api/evals/run?scenario=all|<id>` ｜ `GET /api/evals/runs/{runId}`

### 4.2 断言类型（EvalEvaluator 支持的全部检查）

| 断言字段 | 含义 | 失败示例 |
|---|---|---|
| `expectedVerdict` | 期望裁决（`any` = 不检查） | 期望 CONFIRMED，实际 INCONCLUSIVE |
| `minConfidence` | 实际置信度下限 | 期望 ≥0.5，实际 0.3 |
| `expectNeedsHuman` | 期望是否需要人工 | 期望 true，实际 false |
| `requiredTools` | 证据链中必须出现过的工具 | 从未调用 `query_metric` |
| `mustContain` | 报告文本必须包含的子串 | 报告里没有 "No log lines matched" |

### 4.3 内置 3 个样例的期望输出

**样例 1 `demo-app-down`（真故障确认）**
- 告警：`DemoAppDown`，service=demo-app；断言：CONFIRMED、置信度≥0.5、必调 `query_metric`+`fetch_runbook`、报告含 `up{job="demo-app"}`
- 期望行为：查 `up{job="demo-app"}`=0（指标证实不可达）→ 取 runbook 按步骤排查 → 结合无日志/无部署 → 裁决 CONFIRMED
- 通过 = 4 条断言全过；典型失败：裁决变成 INCONCLUSIVE（证据不足）、或没调 runbook

**样例 2 `demo-app-down-honest-logs`（防幻觉）**
- 告警：`DemoAppDown`，service=demo-app（与样例 1 是不同 fingerprint，会创建独立 Case）
- 断言：裁决不检查（`any`）、必调 `search_logs`、报告必须含 **"No log lines matched"**
- 期望行为：真的去 Loki 查日志，且因为 demo-app 没有任何日志，**如实报告"没有日志"**——绝不允许编造日志内容
- 通过 = 调用了 search_logs 且报告如实出现空结果；典型失败：agent 没查日志就下结论、或幻觉式地说"日志显示 XX 错误"

**样例 3 `unknown-service`（未知服务转人工）**
- 告警：`GhostServiceDown`，service=ghost-service（拓扑中不存在）
- 断言：裁决不检查、`expectNeedsHuman: true`、必调 `get_topology`
- 期望行为：调 get_topology 确认服务未知 → 明确说明"不在资产拓扑中" → 不猜测 → INCONCLUSIVE + needsHuman=true
- 通过 = needsHuman=true 且调用了 get_topology；典型失败：agent 编造根因说"服务挂了"却不需要人工

### 4.4 期望输出长什么样（以样例 1 通过为例）

```json
{
  "runId": "f5f5ad35-...",
  "passed": 3, "total": 3, "skipped": false,
  "results": [{
    "scenarioId": "demo-app-down",
    "expectedVerdict": "CONFIRMED",
    "actualVerdict": "CONFIRMED",
    "actualConfidence": 0.9,
    "actualNeedsHuman": true,
    "evidenceTools": ["fetch_runbook", "get_alert_detail", "query_metric", "search_logs", "get_topology"],
    "passed": true,
    "failures": [],
    "caseId": "887c37c3-..."
  }]
}
```

Markdown 报告（`evals/report-<runId>.md`）：

```
| Scenario | Verdict (exp -> act) | Confidence | needsHuman | Tools | Result |
| demo-app-down | CONFIRMED -> CONFIRMED | 0.90 | true | fetch_runbook,get_alert_detail,query_metric,search_logs | ✅ PASS |
```

> 注意：`needsHuman=true` + CONFIRMED 是**合规通过**——样例 1 的恢复需要 kubectl 写操作，转人工是设计行为，断言里没要求 needsHuman=false。

### 4.5 运行前提与常见失败

- 前提：LLM Key 已配置（`/api/info` 的 `llmConfigured=true`）+ docker 底座在线（Loki/Prometheus 可查）
- 未配 Key：所有场景 `passed=false` + `skipped=true` + 明确提示——这是**设计行为**，不是 bug
- 场景失败先看 `failures` 列表逐条对照；LLM 结果有波动，单次失败先重跑 2-3 次再判断是"真退化"还是"随机波动"

## 5. 测试失败排查速查

1. **先看是 Failures 还是 Errors**：
   - Failures → 打开失败断言的行，对比"期望值 vs 实际值"，判断是代码回归还是断言过时；
   - Errors → 看堆栈顶部的异常类型与行号。
2. **单跑该测试**：`.\mvnw.cmd test -Dtest=类名#方法名`，隔离问题。
3. **检查是否动了共享代码**：状态机改了 → `CaseStateMachineTest`；工具改了 → `ToolExecutorTest`；客户端格式改了 → `LokiClientTest`。
4. **端到端失败**：先 `docker ps` 确认 6 个容器都在；再 `curl http://localhost:8080/api/info` 确认应用存活；再看应用日志。

## 6. 常见坑

- **PowerShell 控制台中文乱码**：是控制台编码（GBK）显示问题，接口数据本身是 UTF-8，不影响功能。
- **`LokiClientTest` 的 query 参数断言**：LogQL 含特殊字符会被 URL 编码，断言时用 `requestTo(containsString(...))` 匹配路径 + `queryParam("limit", ...)` 匹配未编码参数，不要断言编码后的 query 值。
- **Loki query_range 的 start/end 是纳秒**（Prometheus 是毫秒）——传毫秒会把查询窗口推到 1970 年，结果恒为空。已修复并加了回归断言（`LokiClientTest` 校验 ns 值）。
- **Loki 会拒绝"过旧"的 push**（实测 3.x 约 1 小时窗口，`reject_old_samples_max_age` 配置在某些版本不可靠，见 grafana/loki#18669）：评测日志注入脚本的时间戳全部落在最近 5~12 分钟内。
- **Loki 的查询只读得到"已 flush"的块**：单二进制模式下 querier 不能稳定访问 ingester 内存头，默认 `chunk_idle_period=30m` 意味着 push 后 30 分钟数据才可见——评测会看到旧数据或空结果。已配置 `ingester.chunk_idle_period: 15s` + `flush_check_period: 5s`（push 后 ~20 秒可见）。此外实测发现**不同 label 选择器的可见性也可能短暂不一致**（评测中 `{service_name=...}` 可查而 `{job=...}`/`{pod=...}` 为空，疑似 TSDB 索引同步延迟，3.1.1 的 `tsdb_shipper.resync_interval` 配置键不可用、未能确认）——因此注入脚本末尾**等待 75 秒 + 双重自检**（`{job="eval"}` 与 `{pod="payment-api"}` 都必须返回数据）才输出 `VERIFY OK`，出现 `VERIFY OK` 后再跑评测。
- **测试不依赖外部服务**：数据库用 H2 内存库，HTTP 用 MockRestServiceServer，Redis 去重用 InMemoryDeduplicator——没有 Docker 也能跑 `mvnw test`。
