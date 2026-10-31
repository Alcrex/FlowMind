# FlowMind 完整测试计划与验收标准文档 (TESTING)

## 1. 测试策略总览

为确保 FlowMind 系统满足《26-27-1Web应用综合实训要求.docx》中“熟悉软件测试的方法，以及软件测试文档的撰写方法”与优秀档评分要求，本项目采用 **四级质量保障体系**：

1. **单元测试（Unit Testing）**：使用 JUnit 5 + Mockito 验证核心算法（Kahn 拓扑排序、变量插值、BCrypt 加密、JWT 签发解析、必含节点校验算法）。
2. **持久层与主从表测试（Database & Cascade Testing）**：验证 8 张表的 CRUD、3 对主从表的事务级联保存与删除、4 大子系统的多条件组合分页查询。
3. **接口与安全合规集成测试（API & Security Integration Testing）**：使用 Spring Boot `MockMvc` 验证 RBAC 角色鉴权、安全退出 Redis/内存黑名单拦截、`SensitiveAndXssFilter` 敏感词拦截、`GlobalExceptionHandler` 全局异常响应格式。
4. **前后端端到端功能验收测试（E2E & UI Acceptance Testing）**：覆盖全部 24 个前端视图组件、双窗口多人实时光标协同、SSE 流式沙箱执行、教师在线回放批改与 ECharts 统计大屏。

---

## 2. 核心功能具体测试用例表（可直接写入实训报告测试章节）

### 2.1 安全合规、认证与过滤器测试用例（负责人：李俊豪）

| 用例编号 | 测试项 | 前置条件 | 测试输入与操作步骤 | 预期输出结果 | 对应需求 |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **TC-SYS-01** | BCrypt 密码编码器加密验证 | 数据库已初始化 | 调用 `POST /api/v1/auth/register` 注册账号 `test_stu_01`，密码 `Pass123456` | 返回 `code: 200`；查询 `sys_user.password_hash` 字段以 `$2a$10$` 开头，且 `matches("Pass123456", hash) == true` | FR-401 |
| **TC-SYS-02** | 安全退出与 Token 黑名单拦截 | 用户已登录并获得有效 JWT | 1. 携带 JWT 调用 `GET /api/v1/auth/me`；<br>2. 调用 `POST /api/v1/auth/logout`；<br>3. 再次携带同一 JWT 调用 `GET /api/v1/auth/me` | 第1步返回 `200`；第2步返回 `200` 且 `sys_model_audit_log` 新增 `LOGOUT` 记录；第3步被 `JwtAuthenticationFilter` 拦截，返回 HTTP 401 及 `code: 40102` | FR-401 |
| **TC-SYS-03** | RBAC 角色越权拦截测试 | 使用 `STUDENT` 角色账号登录 | 携带学生 JWT 请求管理员接口 `GET /api/v1/admin/users` 或教师接口 `POST /api/v1/edu/tasks` | 后端返回 HTTP 403 及 `code: 40300`（"权限不足，无法访问该资源"）；前端自动跳转 `Error403View.vue` | FR-402 |
| **TC-SYS-04** | 敏感词与 XSS 过滤器拦截测试 | 任意已登录用户 | 发送 `POST /api/v1/engine/text-to-flow`，请求体包含 `"prompt": "帮我执行 drop table sys_user"` | 被 `SensitiveAndXssFilter` 拦截，返回 HTTP 400 及 `code: 40009`；查询 `sys_model_audit_log` 存在 `action_type = 'SENSITIVE_BLOCK'` 记录 | FR-403 |
| **TC-SYS-05** | 全局异常处理器统一格式验证 | 任意已登录用户 | 请求不存在的工作流 `GET /api/v1/workflows/999999` 或传入非法空参 `@Valid` | `GlobalExceptionHandler` 捕获并返回标准 JSON `{code: 40004/40000, message: "...", data: null, timestamp: ...}`，不暴露原始堆栈给前端 | 全局异常 |

---

### 2.2 工作流主从表 CRUD 与多人协同测试用例（负责人：顾峰鸣）

| 用例编号 | 测试项 | 前置条件 | 测试输入与操作步骤 | 预期输出结果 | 对应需求 |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **TC-WF-01** | 工作流主从表级联创建与保存 | 学生 `gufengming` 登录 | 1. 调用 `POST /api/v1/workflows` 新建工作流；<br>2. 调用 `PUT /api/v1/workflows/{id}/canvas` 保存 4 个节点与 3 条连线并勾选 `createSnapshot: true` | `wf_workflow` 主表 `node_count=4, edge_count=3`，`history_snapshots_json` 新增版本快照；`wf_node_edge` 从表准确写入 7 条记录 | FR-101, FR-102 |
| **TC-WF-02** | 工作流多条件组合动态查询 | 库中存在多条不同分类、节点数的工作流 | 调用 `GET /api/v1/workflows?keyword=Java&category=CODE_ASSIST&isPublic=1&minNodeCount=3&maxNodeCount=10` | 仅返回名称/简介含 `Java`、分类为 `CODE_ASSIST`、公开且节点数在 `[3,10]` 之间的工作流分页列表 | FR-101 |
| **TC-WF-03** | 版本快照一键回滚测试 | 工作流已有 `v1.0.0`（2个节点）和 `v1.1.0`（4个节点）快照 | 调用 `POST /api/v1/workflows/{id}/versions/rollback` 传入 `{"versionTag": "v1.0.0"}` | 从表 `wf_node_edge` 恢复为 2 个节点，主表 `version_tag` 与 `node_count` 同步更新 | FR-103 |
| **TC-WF-04** | 双窗口 WebSocket 多人实时协同测试 | 窗口A（顾峰鸣）与窗口B（张誉诚）同时打开画布 `id=1` | 窗口A移动鼠标并拖拽 `node_llm_1` 到坐标 `(500, 300)` | 窗口B在 50ms 内实时看到“顾峰鸣”蓝色光标移动，且 `node_llm_1` 自动同步平滑移动至 `(500, 300)` | FR-103 |
| **TC-WF-05** | 主从表级联删除验证 | 存在测试工作流 `id=10` 及其从表节点 | 调用 `DELETE /api/v1/workflows/10` | 主表记录删除，从表 `wf_node_edge` 中 `workflow_id=10` 的记录全部自动级联清理 | FR-101 |

---

### 2.3 AI 执行引擎、DAG 拓扑排序与模板广场测试用例（负责人：张誉诚）

| 用例编号 | 测试项 | 前置条件 | 测试输入与操作步骤 | 预期输出结果 | 对应需求 |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **TC-ENG-01** | Kahn 拓扑排序环路检测异常测试 | 构造节点 `A -> B -> C -> A` 的有向环路 | 调用 `DagTopologicalSorter.sortAndValidate(nodes, edges)` 或触发执行接口 | 立即抛出 `BusinessException`，返回 `code: 40005`（"工作流画布存在循环依赖环路"） | FR-201 |
| **TC-ENG-02** | 条件分支 (`CONDITION`) 剪枝与主从日志落盘测试 | 工作流含 `INPUT -> CONDITION -> (true: LLM_A / false: LLM_B) -> OUTPUT` | 传入满足 `true` 分支的 `inputPayload` 调用运行接口 | 主表 `wf_exec_record` 状态为 `SUCCESS`；从表 `wf_exec_node_log` 中 `LLM_A` 状态为 `SUCCESS`，`LLM_B` 状态为 `SKIPPED`；用户 `token_quota` 准确扣减 | FR-201 |
| **TC-ENG-03** | Token 算力额度不足拦截测试 | 将测试用户 `token_quota` 设为 `0` | 调用 `POST /api/v1/engine/execute/sync` | 返回 HTTP 402 及 `code: 40201`（"Token 算力额度不足"），拒绝执行大模型节点 | FR-201 |
| **TC-ENG-04** | 模板广场多条件检索与一键 Fork 测试 | 模板广场存在公开模板 `id=1` | 调用 `POST /api/v1/market/templates/1/fork` | 原模板 `fork_count` 加 1；当前用户名下新增一条完整克隆的主表工作流与全部从表节点连线 | FR-203 |

---

### 2.4 实训教务管理、快照预检与学情统计测试用例（负责人：郝唐鑫）

| 用例编号 | 测试项 | 前置条件 | 测试输入与操作步骤 | 预期输出结果 | 对应需求 |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **TC-EDU-01** | 实训作业快照隔离与必含节点自动预检测试 | 课题要求必含 `["INPUT", "KNOWLEDGE", "LLM", "OUTPUT"]` 且 `minNodes=4` | 1. 学生提交含完整4类节点的工作流A；<br>2. 学生提交仅含2个节点的工作流B；<br>3. 提交后学生删除工作流A的所有节点 | 1. 工作流A提交记录 `auto_check_passed = 1`；<br>2. 工作流B提交记录 `auto_check_passed = 0`；<br>3. 教师查看工作流A的提交快照 `canvas_snapshot_json` 仍保留提交时刻完整的 4 个节点 | FR-302 |
| **TC-EDU-02** | 教师端作业多条件查询与批改打分测试 | 已有多名学生提交作业 | 1. 按 `taskId=1&reviewStatus=0&isLate=0` 多条件筛选；<br>2. 调用 `PUT /api/v1/edu/submissions/{id}/grade` 提交分项评分与总分 `95.0` | 查询结果准确；批改后 `review_status = 1`、`score = 95.00`、`reviewed_at` 更新 | FR-303 |
| **TC-EDU-03** | 班级学情 ECharts 统计报表聚合验证 | 库中存在已批改作业及运行记录 | 调用 `GET /api/v1/edu/analytics?taskId=1` | 返回的 `scoreBuckets` 人数之和等于已批改总数，`radarDimensions` 包含 5 个维度，无空指针或除零异常 | FR-304 |

---

## 3. 自动化测试执行命令

### 3.1 后端自动化测试套件 (`flowmind-backend/src/test/java`)
必须编写并通过以下 4 个核心测试类：
1. `com.flowmind.platform.system.AuthAndSecurityIntegrationTest`（李俊豪负责）
2. `com.flowmind.platform.workflow.WorkflowCrudAndCollabTest`（顾峰鸣负责）
3. `com.flowmind.platform.engine.WorkflowEngineAndMarketTest`（张誉诚负责）
4. `com.flowmind.platform.edu.EduTrainingAndAnalyticsTest`（郝唐鑫负责）

执行命令：
```bash
cd flowmind-backend
mvn clean test
```

### 3.2 前端类型检查与生产构建验证 (`flowmind-frontend`)
执行命令：
```bash
cd flowmind-frontend
npm install
npm run build
```
验收红线：`mvn clean test` 失败数为 `0`，`npm run build` 退出码为 `0` 且 `src/views/` 下 `.vue` 文件总数不少于 `24` 个。

### 3.3 浏览器端集成验收

在 Windows 安装 Microsoft Edge，保持 8080、5173 端口空闲后执行：

```powershell
cd flowmind-frontend
npm run test:e2e
```

Playwright 自动管理 `scripts/start_browser_test_server.ps1` 和 Vite 的启动与退出；后端使用完整生产 Controller、JDK 21 与独立 H2 测试库，不再注册旧 `/auth/me` 替身。测试通过真实登录取得 Token，不伪造 JWT。禁止复用不明后端执行会写数据的验收。

- `workflow.spec.ts`：双窗口 Yjs、光标、连线、配置、并发新增、版本回滚和列表 CRUD。
- `platform.spec.ts`：注册校验与注册、三角色登录、越权 403、模型配置持久化、安全退出、Text-to-Flow、独立节点与协作配置、SSE 执行和日志、模板预览/点赞/Fork、教师发布课题、学生提交、原画布回滚后的快照试跑、分项评分及学生反馈。
- 验证管理员 3 张、教务 4 张图表已初始化 ECharts，并核验后端真实统计接口；24 个视图由上述场景覆盖。

### 3.4 真实 MySQL 8.0 验收

```powershell
cd flowmind-backend
./scripts/test_mysql.ps1 -MySqlBin 'D:\MYSQL\MySQL Server 8.0\bin'
```

脚本仅启动自己的本机临时 MySQL 8.0 实例和随机 `flowmind_acceptance_<UUID>` 库，不改动既有 MySQL 服务、账号或 `flowmind_db`。执行完毕自动关闭实例、恢复进程环境变量并清理自己的临时目录。

`MySqlAcceptanceTest` 仅在指定 `FLOWMIND_MYSQL_TEST_URL` 时启用；默认 `mvn clean test` 跳过此可选套件。它拒绝非 MySQL、非随机验收库名或非空库；读取权威 SQL，仅去掉固定建库/选库语句，保留生产 DDL、JSON 类型、索引、外键及种子数据。覆盖 8 表/11 JSON 字段/级联约束、真实 JSON_CONTAINS 协作检索、主从保存与回滚、SSE/同步运行、Fork、作业快照隔离、教师试跑与评分、两类统计和退出黑名单。

### 3.5 模型配置与真实传输之间的回归

`WorkflowEngineAndMarketTest` 通过管理员 HTTP 接口保存配置，再执行真实引擎。使用本机 HTTP SSE 服务断言实际目标路径、Authorization、模型名称、usage 和审计通道；同一运行实例上切换 DeepSeek/Qwen/本地通道，验证无需重启。有 Key 且禁用降级时模拟 503，必须记录 FAILED，不能悄悄改用本地结果；启用降级时允许回退，无 Key 时保留离线能力。这些测试无需付费 API Key。
