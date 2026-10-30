# FlowMind 架构一致性审查与最终确认报告 (FINAL_REVIEW)

> 本文件与项目根目录 `FINAL_REVIEW.md` 保持同步，供 `docs/` 目录内查阅。

## 1. 已完成的设计与文档清单

本次架构设计与文档工程已完整生成 **14 份核心工程文件**，覆盖从需求、架构、数据库、接口、算法、任务分解、Git 协作、测试计划到 Codex 执行指令的全部环节：

| 序号 | 文件路径 | 核心产出内容摘要 | 状态 |
| :---: | :--- | :--- | :---: |
| 1 | `AGENTS.md` | Codex 项目级开发宪章、十条强制铁律、必读文档索引 | ✅ 已完成 |
| 2 | `README.md` | 项目介绍、4人分工、环境配置、一键启动说明与预置演示账号 | ✅ 已完成 |
| 3 | `docs/PROJECT_OVERVIEW.md` | 选题背景、核心目标、课程评分硬指标达标矩阵、技术栈选型 | ✅ 已完成 |
| 4 | `docs/SRS.md` | 4大子系统详细软件需求规格（输入/输出/处理流程/异常/验收标准） | ✅ 已完成 |
| 5 | `docs/ARCHITECTURE.md` | 系统总体架构图、四层职责、模块依赖、过滤器链、WebSocket与SSE架构 | ✅ 已完成 |
| 6 | `docs/DATABASE.md` | 8张数据表字典、3对主从关系、ER图及完整可执行 SQL | ✅ 已完成 |
| 7 | `docs/API.md` | 统一响应结构、全局错误码及全部 4 大子系统接口详细契约 | ✅ 已完成 |
| 8 | `docs/PROJECT_STRUCTURE.md` | 前后端完整目录树、类名定义、24个视图文件映射及防冲突边界 | ✅ 已完成 |
| 9 | `docs/BUSINESS_LOGIC.md` | Kahn拓扑算法、6类节点执行器、AI双模适配器、实时协同与作业快照预检 | ✅ 已完成 |
| 10 | `docs/DEVELOPMENT_TASKS.md` | `TASK-001` ~ `TASK-028` 原子开发任务分解与负责人映射 | ✅ 已完成 |
| 11 | `docs/GIT_WORKFLOW.md` | GitHub 分支策略、Commit 规范、PR 自检与 Code Review 规则 | ✅ 已完成 |
| 12 | `docs/TESTING.md` | 单元测试、主从级联测试、安全过滤器测试与核心测试用例表 | ✅ 已完成 |
| 13 | `docs/CODEX_GUIDE.md` | Codex 开发批次顺序及 5 套可直接复制使用的开发 Prompt 模板 | ✅ 已完成 |
| 14 | `sql/init_flowmind.sql` | 可直接在 MySQL 8.0 运行的 8 张表建表语句与初始演示种子数据 | ✅ 已完成 |

---

## 2. 架构一致性审查记录与已解决的设计冲突

1. **【已解决】24 个视图文件在 4 位成员间的精确均分**：将 `VIEW-03 (views/error/Error403View.vue)` 明确划归负责 RBAC 权限治理的李俊豪，实现顾峰鸣（VIEW 04~09）、张誉诚（VIEW 10~15）、郝唐鑫（VIEW 16~21）、李俊豪（VIEW 01~03, 22~24）严格每人 6 个视图 + 2 张数据表。
2. **【已解决】外部依赖容错与防过度设计**：DAG 引擎采用纯 Java Kahn 拓扑排序 + 策略模式执行器；`TokenCacheService` 支持 Redis 与内存 `ConcurrentHashMap` 双模回退；`LlmClientAdapter` 支持真实 DeepSeek/Qwen API 与内置智能流式沙箱双模切换，确保离线测试与答辩 100% 稳定通过。
3. **【已解决】实训作业画布快照隔离与在线试跑**：`edu_submission` 固化 `canvas_snapshot_json`，且 `/api/v1/engine/execute/stream` 支持 `canvasOverride` 参数供教师端直接回放试跑学生快照。

---

## 3. 已确认事项与剩余待补充细节

1. **✅ [已确认] GitHub 账号与专属分支绑定关系**：
   - **顾峰鸣（组长 / 系统管理员 `admin`）**：GitHub `Alcrex`（管控 `main` 主分支）
   - **张誉诚（组员 1）**：GitHub `killo.linli`（仅限专属分支 `killo.linli` 提交）
   - **郝唐鑫（组员 2）**：GitHub `changdi-in`（仅限专属分支 `changdi-in` 提交）
   - **李俊豪（组员 3）**：GitHub `lijunhao`（仅限专属分支 `lijunhao` 提交）
2. **✅ [已确认] 指导教师、系统管理员与班级设定**：指导教师统一为 **李甜** (`teacher_litian`)，系统管理员统一为 **顾峰鸣** (`admin`)，所属班级统一为 **计科1班**。
3. **[待确认-01] 学号与组号**：用于第三周最终实训报告文件命名。
4. **[待确认-02] 真实大模型 API Key（可选）**：系统已内置智能流式沙箱，如需真实外网调用可在管理员界面动态配置 DeepSeek/Qwen Key。
