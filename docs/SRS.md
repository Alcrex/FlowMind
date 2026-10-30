# FlowMind 软件需求规格说明书 (SRS)

## 1. 引言

### 1.1 编写目的
本文档为 FlowMind（智流）项目的完整软件需求规格说明书（Software Requirements Specification），旨在向开发团队（顾峰鸣、张誉诚、郝唐鑫、李俊豪）及自动化编程智能体（OpenAI Codex）提供无歧义的功能边界、输入输出定义、校验规则、异常流转与验收标准。

### 1.2 角色定义
系统严格限定以下 3 种枚举角色（对应数据库 `sys_user.role_type` 字段）：
- `STUDENT`：学生 / 工作流创作者（默认注册角色）
- `TEACHER`：指导教师（可发布实训课题、批改评测作业、查看班级统计）
- `ADMIN`：系统管理员（可管理全站用户、角色权限、模型通道、算力配额与安全审计）

---

## 2. 功能模块总览与责任映射

系统共包含 **4 大核心业务子系统**、**24 个前端独立视图**、**8 张数据库表（3 对主从表）**：

| 子系统编号 | 子系统名称 | 负责人 | 包含表（主/从） | 包含视图编号 |
| :---: | :--- | :---: | :--- | :--- |
| **SUB-01** | 可视化工作流编排与多人实时协同子系统 | **顾峰鸣** | `wf_workflow` (主)<br>`wf_node_edge` (从) | VIEW-04 ~ VIEW-09 (共6个) |
| **SUB-02** | AI 调度引擎、运行沙箱与模板生态子系统 | **张誉诚** | `wf_exec_record` (主)<br>`wf_exec_node_log` (从) | VIEW-10 ~ VIEW-15 (共6个) |
| **SUB-03** | 高校实训教务管理与在线评测分析子系统 | **郝唐鑫** | `edu_task` (主)<br>`edu_submission` (从) | VIEW-16 ~ VIEW-21 (共6个) |
| **SUB-04** | RBAC 权限治理、算力管控与全局监控大屏子系统 | **李俊豪** | `sys_user` (基础)<br>`sys_model_audit_log` (支撑) | VIEW-01 ~ VIEW-03,<br>VIEW-22 ~ VIEW-24 (共6个) |

---

## 3. 详细功能需求规格

### 3.1 子系统四（先行基础层）：RBAC 权限治理、算力管控与全局监控大屏（负责人：李俊豪）

#### FR-401：多角色注册、登录与安全退出
- **关联视图**：`VIEW-01 LoginView.vue`, `VIEW-02 RegisterView.vue`, `VIEW-03 Error403View.vue`
- **输入**：
  - 注册：`username` (4~32位字母数字下划线), `password` (6~32位), `realName` (2~32位), `roleType` (`STUDENT` 或 `TEACHER`), `deptClass`, `email`。
  - 登录：`username`, `password`。
- **处理流程**：
  1. 注册时校验 `username` 唯一性，使用 `BCryptPasswordEncoder` 加密密码，学生初始赠送 `token_quota = 50000`，教师赠送 `token_quota = 200000`。
  2. 登录时验证账号密码及 `status == 1`（未被封禁），生成包含 `userId`、`username`、`roleType`、`jti` 的 JWT Token（有效期 24 小时），更新 `last_login_at` 并写入 `sys_model_audit_log` (`action_type = 'LOGIN'`)。
  3. 安全退出时，解析请求头 `Authorization: Bearer <token>` 中的 `jti`，写入 Redis 黑名单 `auth:blacklist:{jti}`，并记录 `LOGOUT` 审计日志。
- **输出**：`{ token, tokenId, userInfo: { id, username, realName, roleType, deptClass, tokenQuota, tokenUsed } }`。
- **异常情况**：
  - 账号已存在 $\rightarrow$ 返回业务码 `40001`（"用户名已被注册"）。
  - 密码错误 $\rightarrow$ 返回业务码 `40101`（"账号或密码错误"）。
  - 账号被封禁 (`status == 0`) $\rightarrow$ 返回业务码 `40301`（"账号已被管理员封禁"）。
- **验收标准**：数据库 `sys_user.password_hash` 必须以 `$2a$` 开头；退出后再次携带原 Token 访问受保护接口必须返回 HTTP 401。

#### FR-402：师生用户多条件组合查询、角色分配与算力充值
- **关联视图**：`VIEW-23 UserAndRbacView.vue`
- **输入（多条件查询）**：`keyword`（模糊匹配账号/姓名）、`roleType`、`deptClass`、`status`、`minQuota`、`maxQuota`、`pageNum`、`pageSize`。
- **处理流程**：管理员可分页查询全站用户；支持修改用户角色（`STUDENT`/`TEACHER`/`ADMIN`）、启用/封禁账号、为用户充值或扣减 `token_quota`（记录 `QUOTA_CHANGE` 审计日志）、重置密码为默认 `123456`（BCrypt 加密）。
- **验收标准**：仅 `ADMIN` 角色可调用 `/api/v1/admin/users/**`，普通学生或教师访问时自动跳转 `Error403View.vue`。

#### FR-403：AI 模型通道配置、敏感词过滤与安全审计日志查询
- **关联视图**：`VIEW-24 ModelAndAuditLogView.vue`
- **输入（多条件查询审计表）**：`username`、`moduleName`、`actionType`、`modelProvider`、`httpStatus`、`startTime`、`endTime`、`pageNum`、`pageSize`。
- **处理流程**：
  1. 管理员可配置系统默认大模型通道（`DEEPSEEK` / `QWEN` / `MOCK_LOCAL`）、API BaseURL、API Key 及敏感词词库（如 `["drop table", "rm -rf", "违法违禁词"]`）。
  2. 全局过滤器 `SensitiveAndXssFilter` 对所有寫请求（POST/PUT）载荷进行扫描，若命中敏感词则直接阻断请求，返回业务码 `40009`（"请求包含违规敏感词，已被安全过滤器拦截"），并自动写入一条 `action_type = 'SENSITIVE_BLOCK'` 的审计日志。
- **验收标准**：在任意输入框或 AI 提示词中输入敏感词提交，接口必须拦截且在 `ModelAndAuditLogView.vue` 中立即查到该条拦截记录。

#### FR-404：全校 AI 实训与算力实时监控可视化大屏
- **关联视图**：`VIEW-22 BigScreenMonitorView.vue`
- **输出图表数据**：
  1. 核心 KPI 卡片：总注册师生数、工作流总数、累计执行次数、全校已消耗总 Token 数。
  2. 算力流向桑基图（Sankey）：`学院/班级 -> 角色类型 -> 模型通道 (DeepSeek/Qwen/Mock)` 的 Token 消耗流转。
  3. 院系班级活跃排行榜（横向柱状图）：按工作流创建数与运行次数排名。
  4. 24 小时 AI 调用与响应耗时趋势（双轴折线图）。

---

### 3.2 子系统一：可视化工作流编排与多人实时协同（负责人：顾峰鸣）

#### FR-101：个人工作台与工作流主从表 CRUD 及多条件查询
- **关联视图**：`VIEW-04 DashboardView.vue`, `VIEW-05 WorkflowListView.vue`
- **输入（多条件查询）**：`keyword`（名称/简介模糊查询）、`category`、`isPublic`、`minNodeCount`、`maxNodeCount`、`onlyMine`（个人创建或参与协作）、`startTime`、`endTime`、`pageNum`、`pageSize`。
- **处理流程**：
  1. 创建工作流时，自动生成唯一 `wf_code`，并在从表 `wf_node_edge` 中自动初始化一个默认的 `INPUT` 开始节点与 `OUTPUT` 结束节点。
  2. 查询单个工作流详情时，主表 `wf_workflow` 与从表 `wf_node_edge` 一对多联查返回完整画布结构。
  3. 删除工作流时，级联删除从表 `wf_node_edge` 关联数据。
- **验收标准**：主从表事务一致，多条件组合筛选结果准确。

#### FR-102：Vue Flow 可视化节点画布与节点属性配置器
- **关联视图**：`VIEW-06 CanvasEditorView.vue`, `VIEW-07 NodeConfigDrawer.vue`
- **支持节点类型（6类）**：
  1. `INPUT`（开始输入节点）：定义工作流入参变量列表（如 `query`, `codeSnippet`）。
  2. `LLM`（大模型调用节点）：配置模型通道、Temperature、System Prompt、User Prompt 模板（支持 `{{input.query}}` 变量占位符）。
  3. `KNOWLEDGE`（知识库检索节点）：配置检索关键词模板、预设知识片段库匹配规则与 TopK。
  4. `CODE`（脚本处理节点）：内嵌 Monaco Editor，编写数据转换逻辑或正则提取规则。
  5. `CONDITION`（条件分支节点）：配置布尔判断表达式（如包含某关键词或长度大于阈值），拥有 `true` 和 `false` 两个输出锚点。
  6. `OUTPUT`（结束输出节点）：汇总并格式化前序节点的输出变量。
- **处理流程**：用户拖拽节点、连线、在抽屉中修改配置后，点击“保存画布”批量全量覆写或增量更新从表 `wf_node_edge`，同时自动更新主表 `node_count` 与 `edge_count`。

#### FR-103：版本快照管理与多人实时同屏协同
- **关联视图**：`VIEW-08 VersionHistoryView.vue`, `VIEW-09 CollabManageModal.vue`
- **多人实时协同流程**：
  1. 工作流创建者可在 `CollabManageModal.vue` 中按学号/姓名搜索同学并添加至 `collaborator_ids`。
  2. 多个成员打开同一工作流画布时，连接 `ws://localhost:8080/ws/collab/{workflowId}?token={jwt}`。
  3. 当任一成员移动鼠标、拖拽节点或增删连线时，通过 WebSocket 发送 `CURSOR_MOVE` 或 `GRAPH_DELTA` 消息（结合 Yjs 状态合并），同房间其他成员画布实时渲染对方的彩色光标（带真实姓名标签）及节点最新坐标。
- **版本回滚流程**：每次点击“发布新版本快照”，将当前画布 `nodes` 和 `edges` 追加到 `wf_workflow.history_snapshots_json`；在 `VersionHistoryView.vue` 中可查看历史版本差异并一键回滚恢复到从表 `wf_node_edge`。

---

### 3.3 子系统二：AI 调度引擎、运行沙箱与模板生态（负责人：张誉诚）

#### FR-201：工作流 DAG 拓扑解析与 SSE 流式运行沙箱
- **关联视图**：`VIEW-10 SandboxRunnerView.vue`
- **输入**：`workflowId`、`inputPayload`（JSON 格式的初始变量值，如 `{"query": "请帮我检查这段Java代码的空指针风险"}`）、`triggerMode`。
- **处理流程**：
  1. 校验当前用户剩余 `token_quota > 0`；若不足则抛出业务码 `40201`（"Token 算力额度不足，请联系管理员或教师充值"）。
  2. 加载 `wf_node_edge` 构建有向图，使用 **Kahn 拓扑排序算法**检测是否存在环路或孤立无效路径；若存在环路则返回业务码 `40005`（"工作流存在循环依赖，无法执行"）。
  3. 创建主表记录 `wf_exec_record` (`exec_status = 'RUNNING'`)。
  4. 按拓扑顺序依次执行各节点（遇到 `CONDITION` 节点时根据条件评估结果剪枝未命中的分支）：
     - 每个节点开始/结束时，通过 **SSE (Server-Sent Events)** 实时向前端推送事件：`NODE_START`、`NODE_TOKEN_CHUNK`（流式打字机文本）、`NODE_FINISH`、`WORKFLOW_COMPLETE`。
     - 每个节点执行完毕后立即写入从表 `wf_exec_node_log`。
  5. 全部完成后更新主表 `wf_exec_record` 的 `exec_status = 'SUCCESS'`、`total_tokens`、`duration_ms`、`final_output`，并扣减用户 `sys_user.token_quota`。
- **验收标准**：前端点击运行后，画布对应节点依次高亮闪烁，抽屉实时流式打印输出，且主从表 `wf_exec_record` 与 `wf_exec_node_log` 数据完整落盘。

#### FR-202：自然语言一句话生成工作流（Text-to-Flow）
- **关联视图**：`VIEW-11 TextToFlowView.vue`
- **输入**：`prompt`（如“帮我生成一个先检索Java规范知识库、再用大模型评审代码并输出评分报告的工作流”）、`category`。
- **处理流程**：后端根据用户自然语言意图（调用大模型 JSON 结构化输出或内置智能模板合成器），自动生成包含 4~6 个合理节点及连线坐标的工作流主从表记录，并返回新生成的 `workflowId`，前端直接跳转进入画布预览和微调。

#### FR-203：运行记录主从表查询与模板广场多条件检索
- **关联视图**：`VIEW-12 ExecRecordListView.vue`, `VIEW-13 ExecNodeDetailView.vue`, `VIEW-14 TemplateMarketView.vue`, `VIEW-15 TemplateDetailView.vue`
- **运行记录多条件查询**：支持按 `workflowId`、`execStatus`、`triggerMode`、`minDuration`、`maxDuration`、`startTime`、`endTime` 查询主表 `wf_exec_record`；点击单条记录进入 `ExecNodeDetailView.vue` 查看从表 `wf_exec_node_log` 的各节点输入输出快照与耗时瀑布图。
- **模板广场多条件查询**：支持按 `keyword`、`category`、`tag`、`minNodeCount`、`maxNodeCount`、`sortBy`（按复用次数 `fork_count` 或点赞数 `like_count` 或最新时间排序）检索公开工作流（`is_public = 1`），支持点赞与**一键 Fork 克隆**到个人空间。

---

### 3.4 子系统三：高校实训教务管理与在线评测分析（负责人：郝唐鑫）

#### FR-301：教师端实训课题发布与规则配置（主表 CRUD）
- **关联视图**：`VIEW-18 TeacherTaskManageView.vue`, `VIEW-16 StudentTaskListView.vue`
- **输入**：`taskTitle`、`courseName`、`targetClass`、`taskRequirement`、`requiredNodesJson`（如要求必含 `INPUT`, `LLM`, `CONDITION`, `OUTPUT` 且节点总数 $\ge 4$）、`rubricJson`、`deadline`。
- **处理流程**：教师创建、修改、关闭或删除实训课题（主表 `edu_task`）；学生端在 `StudentTaskListView.vue` 中按课程名、状态、关键词查看面向自己班级的实训课题及剩余倒计时。

#### FR-302：学生端实训作业快照提交与合规自动预检（从表写入）
- **关联视图**：`VIEW-17 SubmissionEditView.vue`
- **输入**：`taskId`、`workflowId`、`studentReport`（不少于 50 字的实训报告）。
- **处理流程**：
  1. 后端自动读取所选 `workflowId` 当前时刻的全部节点与连线，序列化为 `canvas_snapshot_json`（确保学生后续再改个人画布不影响已交作业快照）。
  2. 根据主表 `edu_task.required_nodes_json` 自动校验学生画布是否包含所有必含节点类型且达到最低节点数量，写入 `auto_check_passed` 字段（`1` 通过 / `0` 未达标预警）。
  3. 对比当前时间与 `edu_task.deadline` 自动标记 `is_late`（`0` 按时 / `1` 逾期），写入或更新从表 `edu_submission`。

#### FR-303：教师端作业多条件查询与在线沙箱回放批改
- **关联视图**：`VIEW-19 TeacherGradeListView.vue`, `VIEW-20 TeacherCanvasReviewView.vue`
- **输入（多条件查询）**：`taskId`、`targetClass`、`reviewStatus`（待批改/已批改/打回重做）、`isLate`、`minScore`、`maxScore`、`studentKeyword`（学号/姓名模糊查询）。
- **在线回放与评测流程**：
  1. 教师在 `TeacherCanvasReviewView.vue` 中直接渲染学生作业中的 `canvas_snapshot_json` 画布拓扑。
  2. 教师可输入测试数据，直接触发沙箱试跑该快照工作流，实时观察输出质量。
  3. 教师按 `rubric_json` 填写分项得分，系统自动汇总计算总分 `score`（0~100），录入 `teacher_comment`，将 `review_status` 更新为 `1`（已批改）或 `2`（打回重做）。

#### FR-304：教师端班级学情 ECharts 统计分析
- **关联视图**：`VIEW-21 EduAnalyticsView.vue`
- **输出统计维度**：
  1. **成绩分段分布直方图**：不及格（$<60$）、合格（$60\sim69$）、中等（$70\sim79$）、良好（$80\sim89$）、优秀（$90\sim100$）人数占比。
  2. **班级工程能力五维雷达图**：拓扑设计规范度、大模型 Prompt 质量、分支逻辑完备性、执行成功率、实训报告质量平均得分率。
  3. **近 14 日实训提交与沙箱调试活跃趋势折线图**。
  4. **学生工作流高频报错节点类型分布饼图**（聚合 `wf_exec_node_log` 中 `step_status = 'ERROR'` 的节点类型）。
