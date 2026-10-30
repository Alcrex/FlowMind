# FlowMind 架构一致性审查与最终确认报告 (FINAL_REVIEW)

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

在完成初稿后，我们对 9 个核心维度进行了交叉一致性审查，并主动发现、修复了以下 4 项潜在问题：

### 2.1 【已解决】24 个视图文件在 4 位成员间的精确均分问题
- **发现问题**：在第一阶段初版表格中，虽然汇总列写明“每人 6 个视图”，但在 24 行明细表中，`VIEW-03 (views/error/Error403View.vue)` 曾归在顾峰鸣名下，导致顾峰鸣实际有 7 个视图、李俊豪只有 5 个视图。
- **修复方案**：由于 `Error403View.vue` 直接对应 RBAC 权限不足拦截展示，在 `SRS.md`、`PROJECT_STRUCTURE.md` 和 `DEVELOPMENT_TASKS.md` 中已统一将 `VIEW-03 (Error403View.vue)` 划归负责 RBAC 权限治理的 **李俊豪**。至此，顾峰鸣（VIEW 04~09）、张誉诚（VIEW 10~15）、郝唐鑫（VIEW 16~21）、李俊豪（VIEW 01~03, 22~24）严格达成 **每人 6 个视图 + 2 张数据表** 的绝对均衡。

### 2.2 【已解决】外部依赖导致 Codex 测试或离线答辩失败的风险（防过度设计）
- **发现问题**：原先方案提及 LiteFlow 动态 XML 编译、外部 Redis 强依赖以及外部大模型 API 强依赖。若团队成员本地未安装 Redis 或答辩教室无外网/API 欠费，系统将无法启动或演示失败。
- **修复方案**：
  1. **DAG 执行引擎收敛为纯 Java Kahn 拓扑排序器 + 策略模式节点执行器**（`DagTopologicalSorter` + `WorkflowDagExecutor`），代码零黑盒、100% 可单测；
  2. **`TokenCacheService` 实现 Redis + `ConcurrentHashMap` 双模自动降级**，无 Redis 环境也能完整演示安全退出黑名单拦截；
  3. **`LlmClientAdapter` 实现真实 DeepSeek/Qwen API + 本地确定性智能流式沙箱双模切换**，离线状态下依然能演示逼真的 SSE 打字机流式输出与节点流光高亮。

### 2.3 【已解决】教师批改时学生修改原工作流导致作业失真的问题
- **发现问题**：若 `edu_submission` 仅保存 `workflow_id` 外键，学生提交作业后继续修改画布会导致教师看到的与提交时刻不一致，且教师在 `TeacherCanvasReviewView.vue` 试跑时缺少独立数据源。
- **修复方案**：在 `edu_submission` 表中强制固化 `canvas_snapshot_json` 字段；同时在 `POST /api/v1/engine/execute/stream` 接口的 `WorkflowExecuteReqDTO` 中增加了可选字段 `canvasOverride`，支持教师直接传入快照 JSON 进行在线沙箱回放试跑。

### 2.4 【已解决】数据库字段、后端 Entity/VO 与前端 API 字段命名统一
- **核查结论**：全套文档统一启用 MyBatis-Plus `map-underscore-to-camel-case: true`，数据库下划线字段（如 `token_quota`, `node_count`, `canvas_snapshot_json`）与 Java 驼峰属性及前端 TypeScript 接口属性（`tokenQuota`, `nodeCount`, `canvasSnapshotJson`）保持 100% 严格映射。

---

## 3. 尚未解决的问题与需要项目负责人（顾峰鸣）确认的事项 `[待确认]`

以下信息在当前历史对话中尚未提供，已在相关文档中标记为 **`[待确认]`**，不影响 Codex 立即开始编写代码，但需组长顾峰鸣在实训报告定稿或 GitHub 权限配置时补充：

1. **[待确认-01] GitHub 账号与团队成员姓名的完整映射**：
   - 已知仓库邀请了 `lijunhao`（对应李俊豪）和 `killo.linli`。
   - **待确认**：`killo.linli` 对应 **张誉诚** 还是 **郝唐鑫**？另一位组员以及组长 **顾峰鸣** 本人的 GitHub 用户名分别是什么？
2. **[待确认-02] 团队成员学号、组号与准确专业班级名称**：
   - 根据《26-27-1Web应用综合实训要求.docx》，最终提交压缩包需命名为 `“年级+专业+组号+组长姓名+组长学号+Web应用综合实训报告.docx”`。
   - 目前种子数据中使用了示例班级 `计算机科学与技术2301班`，需确认全组 4 人的真实学号、组号与班级名称。
3. **[待确认-03] 演示环境是否配置真实 DeepSeek / 通义千问 API Key**：
   - 系统默认开启 `enableMockFallback: true`（内置智能流式沙箱），开箱即可演示；若答辩现场需演示调用真实大模型，可在系统启动后由管理员账号登录 `ModelAndAuditLogView.vue` 页面填入真实 API Key。

---

## 4. 推荐的实际开发执行顺序（供组长调度）

建议直接使用 `docs/CODEX_GUIDE.md` 中的提示词模板，按以下 4 步让 Codex 完成全部代码开发：

1. **Step 1（基础底座与安全层）**：先让 Codex 执行 `TASK-001`、`TASK-002`、`TASK-008`、`TASK-009`、`TASK-010`，跑通数据库初始化、Spring Boot 骨架、登录注册、BCrypt 加密、安全退出、敏感词过滤器与管理员接口。
2. **Step 2（核心业务后端）**：接着执行 `TASK-003`、`TASK-004`（工作流主从表与 WebSocket 协同）、`TASK-015` ~ `TASK-017`（DAG 引擎、SSE 流式运行与模板广场）、`TASK-022` ~ `TASK-023`（实训课题、作业快照预检与学情统计）。
3. **Step 3（前端基座与 24 个视图）**：执行 `TASK-005` 搭建前端脚手架，随后依次执行 `TASK-011/012`、`TASK-006/007`、`TASK-018/019`、`TASK-024/025` 完成全部 24 个 `.vue` 视图页面。
4. **Step 4（全量自动化测试与构建验收）**：执行 `TASK-013`、`TASK-020`、`TASK-026`、`TASK-028`，运行 `mvn clean test` 与 `npm run build` 完成最终验收。
