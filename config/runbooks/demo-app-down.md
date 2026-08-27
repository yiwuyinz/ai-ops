---
name: demo-app-down
title: Demo app is down
applicableAlerts: [DemoAppDown]
---

# DemoAppDown 处理 Runbook

## 症状
Prometheus 无法抓取 `demo-app` 的指标，`up{job="demo-app"} == 0`。

## 排查步骤
1. 确认部署是否存在：`kubectl get deploy demo-app -n default`
2. 查看 pod 状态：`kubectl get pods -l app=demo-app`
3. 若 CrashLoopBackOff：`kubectl logs -l app=demo-app --tail=200`
4. 检查资源限制与 OOM：`kubectl describe pod <pod> | grep -A5 Events`

## 处置
- 配置错误 → 修复并重新部署
- 资源不足 → 扩容或调整 limits
- 依赖不可用（DB/Redis 连不上）→ 先恢复依赖再观察

## 备注
这是演示 runbook，请替换为真实内容。
