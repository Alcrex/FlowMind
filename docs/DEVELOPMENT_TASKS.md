# FlowMind 开发任务详细分解表 (DEVELOPMENT_TASKS)

## 1. 任务分解原则与团队成员分工总览

为支持 4 人团队通过 GitHub 分支并行协作、并让 OpenAI Codex 按单任务粒度精准执行，本项目共拆分为 **28 个原子开发任务（`TASK-001` ~ `TASK-028`）**。

| 成员姓名 | 团队角色 | 负责任务编号范围 | 负责数据表 | 负责前端视图 (共24个) | GitHub 账号与强制专属分支 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **顾峰鸣** | 组长 / 管理员 / 架构与协同负责人 | `TASK-001` ~ `TASK-007` (7项) | `wf_workflow` (主)<br>`wf_node_edge` (从) | VIEW-04 ~ VIEW-09 (6个) | `Alcrex` (`main` 主分支) |
| **李俊豪** | 权限安全与大屏负责人 | `TASK-008` ~ `TASK-014` (7项) | `sys_user`<br>`sys_model_audit_log` | VIEW-01 ~ VIEW-03,<br>VIEW-22 ~ VIEW-24 (6个) | `lijunhao` (仅限分支 `lijunhao`) |
| **张誉诚** | AI引擎与模板生态负责人 | `TASK-015` ~ `TASK-021` (7项) | `wf_exec_record` (主)<br>`wf_exec_node_log` (从) | VIEW-10 ~ VIEW-15 (6个) | `killo.linli` (仅限分支 `killo.linli`) |
| **郝唐鑫** | 实训教务与评测统计负责人 | `TASK-022` ~ `TASK-028` (7项) | `edu_task` (主)<br>`edu_submission` (从) | VIEW-16 ~ VIEW-21 (6个) | `changdi-in` (仅限分支 `changdi-in`) |

---

## 2. 第一阶段任务：公共底座与安全治理子系统（优先执行）

### TASK-001：数据库初始化脚本与后端 Maven 工程脚手架搭建
- **负责人**：顾峰鸣
- **依赖任务**：无（首个启动任务）
- **涉及文件**：
  - `sql/init_flowmind.sql`
  - `flowmind-backend/pom.xml`
  - `flowmind-backend/src/main/resources/application.yml`
  - `flowmind-backend/src/main/java/com/flowmind/platform/FlowMindApplication.java`
  - `flowmind-backend/src/main/java/com/flowmind/platform/common/api/Result.java`
  - `flowmind-backend/src/main/java/com/flowmind/platform/common/api/PageResult.java`
  - `flowmind-backend/src/main/java/com/flowmind/platform/common/api/ErrorCode.java`
  - `flowmind-backend/src/main/java/com/flowmind/platform/common/config/MybatisPlusConfig.java`
  - `flowmind-backend/src/main/java/com/flowmind/platform/common/config/OpenApiConfig.java`
- **验收标准**：`mvn clean compile` 编译通过；`sql/init_flowmind.sql` 包含完整 8 张表与初始种子数据。

### TASK-002：全局统一异常处理器与跨域/安全过滤器链框架
- **负责人**：顾峰鸣
- **依赖任务**：`TASK-001`
- **涉及文件**：
  - `common/exception/BusinessException.java`
  - `common/exception/GlobalExceptionHandler.java`
  - `common/filter/CorsFilter.java`
  - `common/config/WebSocketConfig.java`
- **验收标准**：抛出 `BusinessException` 或参数校验失败时，`GlobalExceptionHandler` 返回标准 `Result` JSON 结构且 HTTP 状态码匹配。

### TASK-008：系统用户与安全审计表实体、Mapper 及双模 Token 缓存服务
- **负责人**：李俊豪
- **依赖任务**：`TASK-001`
- **涉及文件**：
  - `system/entity/SysUser.java`
  - `system/entity/SysModelAuditLog.java`
  - `system/mapper/SysUserMapper.java`
  - `system/mapper/SysModelAuditLogMapper.java`
  - `common/security/JwtUtil.java`
  - `common/security/LoginUser.java`
  - `common/security/SecurityUtils.java`
  - `common/security/TokenCacheService.java`
- **验收标准**：支持 JWT 签发与解析；`TokenCacheService` 支持 Redis 与本地内存 `ConcurrentHashMap` 无缝切换，标记黑名单后 `isBlacklisted(jti)` 返回 `true`。

### TASK-009：Spring Security 配置、BCrypt 编码器、JWT 过滤器与敏感词过滤器
- **负责人**：李俊豪
- **依赖任务**：`TASK-002`, `TASK-008`
- **涉及文件**：
  - `common/security/SecurityConfig.java`
  - `common/filter/JwtAuthenticationFilter.java`
  - `common/filter/SensitiveAndXssFilter.java`
  - `system/service/SysAuthService.java`
  - `system/controller/AuthController.java` (`/api/v1/auth/register`, `/login`, `/logout`, `/me`)
- **验收标准**：
  1. 注册密码经 `BCryptPasswordEncoder` 加密存储；
  2. 登录返回有效 JWT；
  3. 调用 `/api/v1/auth/logout` 后原 JWT 立即失效（返回 `40102`）；
  4. 请求体包含 `"drop table"` 等敏感词时被 `SensitiveAndXssFilter` 拦截（返回 `40009`）并写入 `sys_model_audit_log`。

### TASK-010：管理员用户管理、算力配额充值、模型配置与大屏统计后端接口
- **负责人**：李俊豪
- **依赖任务**：`TASK-009`
- **涉及文件**：
  - `system/dto/UserQueryDTO.java`, `UserUpdateDTO.java`, `AuditLogQueryDTO.java`, `ModelConfigDTO.java`
  - `system/vo/LoginRespVO.java`, `SysUserVO.java`, `BigScreenStatsVO.java`
  - `system/service/SysUserService.java`, `SysAuditAndConfigService.java`
  - `system/controller/AdminSystemController.java` (`/api/v1/admin/**`)
- **验收标准**：用户多条件分页查询、角色修改、算力扣减/充值、审计日志多条件查询及 `/api/v1/admin/dashboard/big-screen` 全部通过单元测试。

---

## 3. 第二阶段任务：三大核心业务子系统后端开发（可并行执行）

### TASK-003：工作流主表 (`wf_workflow`) 与节点连线从表 (`wf_node_edge`) 实体及多条件 CRUD
- **负责人**：顾峰鸣
- **依赖任务**：`TASK-009`
- **涉及文件**：
  - `workflow/entity/WfWorkflow.java`, `WfNodeEdge.java`
  - `workflow/mapper/WfWorkflowMapper.java`, `WfNodeEdgeMapper.java`
  - `workflow/dto/WorkflowQueryDTO.java`, `WorkflowCreateDTO.java`, `CanvasSaveDTO.java`
  - `workflow/vo/WorkflowSummaryVO.java`, `WorkflowDetailVO.java`, `WorkspaceDashboardVO.java`
  - `workflow/service/WfWorkflowService.java`
  - `workflow/controller/WfWorkflowController.java`
- **验收标准**：
  1. 新建工作流自动初始化 `INPUT` 和 `OUTPUT` 从表节点；
  2. 多条件查询支持关键词、分类、公开状态、节点数量区间、个人/协作范围筛选；
  3. 画布保存接口在单个事务中更新主表统计与从表节点连线列表，并支持生成历史快照与一键版本回滚。

### TASK-004：WebSocket 多人实时同屏光标与画布协同服务端实现
- **负责人**：顾峰鸣
- **依赖任务**：`TASK-003`
- **涉及文件**：
  - `workflow/websocket/CollabMessage.java`
  - `workflow/websocket/CollabWebSocketHandler.java`
- **验收标准**：连接 `/ws/collab/{workflowId}?token={jwt}` 时完成身份验证、分配成员颜色、广播在线协作者列表，并向同房间其余客户端实时转发 `CURSOR_MOVE` 与 `GRAPH_DELTA` 消息。

### TASK-015：工作流运行主表 (`wf_exec_record`) 与节点日志从表 (`wf_exec_node_log`) 及 Kahn 拓扑排序器
- **负责人**：张誉诚
- **依赖任务**：`TASK-003`, `TASK-010`
- **涉及文件**：
  - `engine/entity/WfExecRecord.java`, `WfExecNodeLog.java`
  - `engine/mapper/WfExecRecordMapper.java`, `WfExecNodeLogMapper.java`
  - `engine/runtime/DagTopologicalSorter.java`
- **验收标准**：`DagTopologicalSorter` 对合法 DAG 返回正确拓扑序列；对存在环路或缺少 `INPUT`/`OUTPUT` 的图抛出 `ErrorCode.DAG_CYCLE_ERROR (40005)`。

### TASK-016：AI 大模型双模适配器与工作流 SSE 流式/同步执行引擎
- **负责人**：张誉诚
- **依赖任务**：`TASK-015`
- **涉及文件**：
  - `engine/runtime/LlmClientAdapter.java`
  - `engine/runtime/WorkflowDagExecutor.java`
  - `engine/dto/WorkflowExecuteReqDTO.java`, `TextToFlowReqDTO.java`, `ExecRecordQueryDTO.java`
  - `engine/vo/ExecRecordDetailVO.java`
  - `engine/service/WfEngineService.java`
  - `engine/controller/WfEngineController.java` (`/api/v1/engine/**`)
- **验收标准**：
  1. 支持 `INPUT`, `KNOWLEDGE`, `CODE`, `CONDITION`, `LLM`, `OUTPUT` 6 类节点按拓扑顺序执行与 `{{node.field}}` 变量插值；
  2. 执行前校验用户算力，执行后写入 `wf_exec_record`（主表）和 `wf_exec_node_log`（从表）并扣减用户 `token_quota`；
  3. `/api/v1/engine/execute/stream` 正常推送 SSE 事件流；`/api/v1/engine/text-to-flow` 可根据自然语言自动创建工作流。

### TASK-017：模板广场多条件组合查询、点赞与一键 Fork 服务
- **负责人**：张誉诚
- **依赖任务**：`TASK-003`, `TASK-016`
- **涉及文件**：
  - `engine/dto/TemplateMarketQueryDTO.java`
  - `engine/service/TemplateMarketService.java`
  - `engine/controller/TemplateMarketController.java` (`/api/v1/market/templates/**`)
- **验收标准**：支持按分类、标签、节点数区间、关键词及热度排序检索公开模板；调用 `/fork` 接口可将目标模板的主从表完整克隆到当前用户空间并将原模板 `fork_count + 1`。

### TASK-022：实训课程任务主表 (`edu_task`) 与学生作业提交从表 (`edu_submission`) 及快照预检服务
- **负责人**：郝唐鑫
- **依赖任务**：`TASK-003`, `TASK-016`
- **涉及文件**：
  - `edu/entity/EduTask.java`, `EduSubmission.java`
  - `edu/mapper/EduTaskMapper.java`, `EduSubmissionMapper.java`
  - `edu/dto/EduTaskQueryDTO.java`, `EduTaskSaveDTO.java`, `SubmissionSubmitDTO.java`
  - `edu/vo/EduTaskVO.java`, `EduSubmissionVO.java`
- **验收标准**：
  1. 教师可发布、修改、删除实训课题（主表 CRUD）；
  2. 学生提交作业时自动固化当前工作流画布为 `canvas_snapshot_json`，自动校验 `required_nodes_json` 写入 `auto_check_passed`，自动判断截止时间写入 `is_late`。

### TASK-023：教师端作业多条件查询、在线批改评分与班级学情 ECharts 统计后端接口
- **负责人**：郝唐鑫
- **依赖任务**：`TASK-022`
- **涉及文件**：
  - `edu/dto/SubmissionQueryDTO.java`, `SubmissionGradeDTO.java`
  - `edu/vo/EduAnalyticsVO.java`
  - `edu/service/EduTrainingService.java`
  - `edu/controller/EduTrainingController.java` (`/api/v1/edu/**`)
- **验收标准**：支持按课题、班级、批改状态、是否逾期、分数段区间、学号姓名多条件查询作业；教师批改打分更新 `score` 与 `review_status`；`/api/v1/edu/analytics` 返回成绩分段、五维雷达、提交趋势与报错节点分布数据。

---

## 4. 第三阶段任务：前端基座与 24 个视图组件开发

### TASK-005：前端 Vite + Vue 3 + TypeScript + Pinia + Vue Router 工程脚手架与主布局
- **负责人**：顾峰鸣
- **依赖任务**：`TASK-001`
- **涉及文件**：
  - `flowmind-frontend/package.json`, `vite.config.ts`, `tsconfig.json`, `index.html`
  - `src/main.ts`, `src/App.vue`
  - `src/utils/request.ts`, `src/stores/authStore.ts`, `src/stores/canvasStore.ts`
  - `src/router/index.ts`, `src/layouts/MainLayout.vue`
- **验收标准**：配置全量 24 个视图路由与前置角色权限守卫；Axios 自动携带 Bearer Token 并在遇到 401/403 时正确跳转。

### TASK-011：前端登录页、注册页与 403 权限拦截异常页开发（3 个视图）
- **负责人**：李俊豪
- **依赖任务**：`TASK-005`, `TASK-009`
- **涉及文件**：
  - `src/api/authAndAdminApi.ts`
  - `src/views/auth/LoginView.vue` (`VIEW-01`)
  - `src/views/auth/RegisterView.vue` (`VIEW-02`)
  - `src/views/error/Error403View.vue` (`VIEW-03`)
- **验收标准**：支持三端角色快捷演示登录、新用户注册校验、顶部导航安全退出及越权访问 403 页面展示。

### TASK-012：前端管理员全局监控大屏、用户 RBAC 管理与审计日志视图开发（3 个视图）
- **负责人**：李俊豪
- **依赖任务**：`TASK-010`, `TASK-011`
- **涉及文件**：
  - `src/views/admin/BigScreenMonitorView.vue` (`VIEW-22`)
  - `src/views/admin/UserAndRbacView.vue` (`VIEW-23`)
  - `src/views/admin/ModelAndAuditLogView.vue` (`VIEW-24`)
- **验收标准**：大屏渲染 ECharts 算力流向桑基图、班级排行与 24h 趋势图；用户管理支持多条件查询、角色切换与算力充值；模型与审计页支持配置 API Key/敏感词并多条件查询安全日志。

### TASK-006：前端个人工作台、工作流多条件列表、版本历史与协同管理视图开发（4 个视图）
- **负责人**：顾峰鸣
- **依赖任务**：`TASK-003`, `TASK-005`
- **涉及文件**：
  - `src/api/workflowApi.ts`
  - `src/views/workspace/DashboardView.vue` (`VIEW-04`)
  - `src/views/workflow/WorkflowListView.vue` (`VIEW-05`)
  - `src/views/workflow/VersionHistoryView.vue` (`VIEW-08`)
  - `src/views/workflow/CollabManageModal.vue` (`VIEW-09`)
- **验收标准**：实现工作流多条件筛选、新建、克隆、删除、历史快照对比回滚及小组成员邀请管理。

### TASK-007：前端 Vue Flow 可视化拖拽画布、节点配置抽屉与多人实时光标协同（2 个核心视图）
- **负责人**：顾峰鸣
- **依赖任务**：`TASK-004`, `TASK-006`
- **涉及文件**：
  - `src/utils/collabSocket.ts`
  - `src/views/workflow/CanvasEditorView.vue` (`VIEW-06`)
  - `src/views/workflow/NodeConfigDrawer.vue` (`VIEW-07`)
- **验收标准**：支持 6 类节点拖拽添加、连线、删除与参数配置（含代码/Prompt 编辑）；打开两个浏览器窗口进入同一画布时，实时显示对方彩色鼠标光标并同步节点拖拽位置。

### TASK-018：前端 SSE 流式调试沙箱与自然语言一句话生成工作流视图开发（2 个视图）
- **负责人**：张誉诚
- **依赖任务**：`TASK-007`, `TASK-016`
- **涉及文件**：
  - `src/api/engineAndMarketApi.ts`
  - `src/utils/sseClient.ts`
  - `src/views/engine/SandboxRunnerView.vue` (`VIEW-10`)
  - `src/views/engine/TextToFlowView.vue` (`VIEW-11`)
- **验收标准**：点击运行后实时接收 SSE 事件流，各节点状态依次流光高亮，输出内容呈打字机流式显示；Text-to-Flow 输入一句话即可生成工作流并跳转画布。

### TASK-019：前端运行记录主从表查询视图与模板广场多条件检索视图开发（4 个视图）
- **负责人**：张誉诚
- **依赖任务**：`TASK-017`, `TASK-018`
- **涉及文件**：
  - `src/views/engine/ExecRecordListView.vue` (`VIEW-12`)
  - `src/views/engine/ExecNodeDetailView.vue` (`VIEW-13`)
  - `src/views/market/TemplateMarketView.vue` (`VIEW-14`)
  - `src/views/market/TemplateDetailView.vue` (`VIEW-15`)
- **验收标准**：运行记录支持多条件筛选，详情页展示从表各节点输入输出与 ECharts 耗时瀑布图；模板广场支持多条件筛选、画布拓扑预览、点赞与一键 Fork。

### TASK-024：前端学生端实训任务列表、作业快照提交与教师端课题管理视图（3 个视图）
- **负责人**：郝唐鑫
- **依赖任务**：`TASK-005`, `TASK-022`
- **涉及文件**：
  - `src/api/eduApi.ts`
  - `src/views/edu/StudentTaskListView.vue` (`VIEW-16`)
  - `src/views/edu/SubmissionEditView.vue` (`VIEW-17`)
  - `src/views/edu/TeacherTaskManageView.vue` (`VIEW-18`)
- **验收标准**：教师可发布与管理实训课题并配置必含节点规则；学生可选择个人工作流提交作业快照并填写实验报告，实时反馈必含节点预检结果。

### TASK-025：前端教师端作业多条件批改列表、画布在线回放评测与学情统计分析视图（3 个视图）
- **负责人**：郝唐鑫
- **依赖任务**：`TASK-023`, `TASK-024`
- **涉及文件**：
  - `src/views/edu/TeacherGradeListView.vue` (`VIEW-19`)
  - `src/views/edu/TeacherCanvasReviewView.vue` (`VIEW-20`)
  - `src/views/edu/EduAnalyticsView.vue` (`VIEW-21`)
- **验收标准**：教师端支持多条件筛选作业、在浏览器端直接渲染学生提交的 `canvas_snapshot_json` 并一键沙箱试跑、录入分项评分与评语；学情分析页完整渲染 4 张 ECharts 统计图表。

---

## 5. 第四阶段任务：系统集成测试与联调验收

| 任务编号 | 任务名称 | 负责人 | 验收标准 |
| :---: | :--- | :---: | :--- |
| `TASK-013` | 认证、RBAC 权限、BCrypt、安全退出与敏感词过滤器自动化测试 | 李俊豪 | `AuthAndSecurityIntegrationTest` 全部通过 |
| `TASK-014` | 管理员端大屏统计与审计日志联调验收 | 李俊豪 | 大屏图表与审计查询零报错 |
| `TASK-020` | Kahn 拓扑排序与工作流主从表执行引擎单元测试 | 张誉诚 | `WorkflowEngineIntegrationTest` 全部通过 |
| `TASK-021` | 模板广场 Fork 与 Text-to-Flow 联调验收 | 张誉诚 | 克隆后的工作流节点连线 100% 完整 |
| `TASK-026` | 实训课题主从表、必含节点预检与学情统计自动化测试 | 郝唐鑫 | `EduTrainingIntegrationTest` 全部通过 |
| `TASK-027` | 教师在线沙箱回放与分项批改全流程联调验收 | 郝唐鑫 | 学生提交 $\rightarrow$ 教师回放试跑 $\rightarrow$ 批改统计闭环通过 |
| `TASK-028` | 全栈前后端构建验证（`mvn test` + `npm run build`）与 24 视图完整性核验 | 顾峰鸣 | 后端测试 100% 绿灯，前端 TypeScript 编译打包零错误，24 个 `.vue` 视图齐全 |
