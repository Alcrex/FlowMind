# 自动化预演证据

2026-11-05生成。来源：7组Edge浏览器测试访问生产dist产物，后端为独立H2测试库；mysql.txt是独立MySQL8.0.42验收，不能据此宣称浏览器已直连MySQL。现场七步仍需四位成员在实际演示机签字验收。

截图按step-1至step-7编号。step-2-security.json记录角色、敏感词审计和退出状态码，不含Token；step-7-monitor-delta.json记录执行前后实际统计。step-3是关闭全部画布前和重开后的位置核对；step-4分别显示4/7/3节点、规则标识与条件支路跳过；step-5显示40005；step-6显示冻结快照评分与95分学情。

backend.txt：70项，67通过、3项MySQL默认跳过；mysql.txt：3项通过、0跳过；build.txt：生产构建；e2e.txt：7组通过，无重试。测试执行后清理临时工作流和课题，截图里的业务ID仅供预演追溯，不是演示当天可复用的正式数据。

操作手册见 ../../docs/DEFENSE_RUNBOOK.md，12项整改与剩余条件见 ../../docs/REMEDIATION_20261105.md。
