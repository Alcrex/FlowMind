# FlowMind 项目完整目录结构与代码组织规范 (PROJECT_STRUCTURE)

## 1. 仓库顶层目录结构

```text
FlowMind/
├── AGENTS.md                           # Codex 项目级最高开发宪章与行为准则
├── README.md                           # 项目介绍、环境要求、一键启动与部署说明
├── FINAL_REVIEW.md                     # 架构一致性审查与待确认事项汇总
├── docs/                               # 全套软件工程开发文档 (12份)
│   ├── PROJECT_OVERVIEW.md             # 1. 项目总体概述
│   ├── SRS.md                          # 2. 软件需求规格说明书
│   ├── ARCHITECTURE.md                 # 3. 系统总体架构设计
│   ├── DATABASE.md                     # 4. 数据库详细设计与建表SQL
│   ├── API.md                          # 5. RESTful / SSE / WebSocket 接口规范
│   ├── PROJECT_STRUCTURE.md            # 6. 项目完整目录树与模块职责 (本文档)
│   ├── BUSINESS_LOGIC.md               # 7. 核心算法、DAG引擎与协同逻辑设计
│   ├── DEVELOPMENT_TASKS.md            # 8. 4人团队开发任务分解表 (TASK-001 ~ TASK-028)
│   ├── GIT_WORKFLOW.md                 # 9. GitHub 分支管理与 PR 协作规范
│   ├── TESTING.md                      # 10. 单元测试、接口测试与验收测试计划
│   ├── CODEX_GUIDE.md                  # 11. Codex 专属执行指南与提示词模板
│   └── FINAL_REVIEW.md                 # 12. 架构一致性审查报告副本
├── sql/
│   └── init_flowmind.sql               # 可直接执行的 MySQL 8.0 建表与演示种子数据脚本
├── flowmind-backend/                   # Spring Boot 3.3 (JDK 21) 后端工程
└── flowmind-frontend/                  # Vue 3.5 (TypeScript + Vite) 前端工程
```

---

## 2. 后端工程目录树 (`flowmind-backend/`)

后端基础包名统一为：`com.flowmind.platform`，按 `common`、`system`、`workflow`、`engine`、`edu` 五大包进行物理隔离：

```text
flowmind-backend/
├── pom.xml                                                 # Maven 依赖管理 (Spring Boot 3.3.5, MyBatis-Plus 3.5.7, JJWT)
└── src/
    ├── main/
    │   ├── java/com/flowmind/platform/
    │   │   ├── FlowMindApplication.java                    # Spring Boot 启动类 (@MapperScan)
    │   │   │
    │   │   ├── common/                                     # 【公共基础层 - 顾峰鸣 & 李俊豪共建】
    │   │   │   ├── api/
    │   │   │   │   ├── Result.java                         # 统一返回包装类 {code, message, data, timestamp}
    │   │   │   │   ├── PageResult.java                     # 统一分页包装类 {records, total, pageNum, pageSize}
    │   │   │   │   └── ErrorCode.java                      # 全局标准错误码枚举
    │   │   │   ├── exception/
    │   │   │   │   ├── BusinessException.java              # 自定义业务异常
    │   │   │   │   └── GlobalExceptionHandler.java         # @RestControllerAdvice 全局异常处理器 (必查项)
    │   │   │   ├── filter/
    │   │   │   │   ├── CorsFilter.java                     # 跨域安全过滤器 (Order=1)
    │   │   │   │   ├── SensitiveAndXssFilter.java          # XSS转义与AI敏感词拦截过滤器 (Order=2, 必查项)
    │   │   │   │   └── JwtAuthenticationFilter.java        # JWT认证与Redis黑名单过滤器 (Order=3, 必查项)
    │   │   │   ├── security/
    │   │   │   │   ├── SecurityConfig.java                 # Spring Security 6 配置 + BCryptPasswordEncoder (必查项)
    │   │   │   │   ├── JwtUtil.java                        # JWT 签发与解析工具类
    │   │   │   │   ├── LoginUser.java                      # 当前登录用户上下文对象
    │   │   │   │   ├── SecurityUtils.java                  # 获取当前登录 userId / roleType 静态工具
    │   │   │   │   └── TokenCacheService.java              # Redis/内存双模 Token 黑名单服务 (安全退出支撑)
    │   │   │   └── config/
    │   │   │       ├── MybatisPlusConfig.java              # 分页插件配置
    │   │   │       ├── WebSocketConfig.java                # WebSocket 端点注册配置
    │   │   │       └── OpenApiConfig.java                  # Knife4j / Swagger 接口文档配置
    │   │   │
    │   │   ├── system/                                     # 【子系统四：权限治理与监控大屏 - 负责人：李俊豪】
    │   │   │   ├── entity/
    │   │   │   │   ├── SysUser.java                        # 表1: sys_user 实体
    │   │   │   │   └── SysModelAuditLog.java               # 表2: sys_model_audit_log 实体
    │   │   │   ├── mapper/
    │   │   │   │   ├── SysUserMapper.java                  # 用户表 Mapper
    │   │   │   │   └── SysModelAuditLogMapper.java         # 审计日志与大屏聚合统计 Mapper
    │   │   │   ├── dto/
    │   │   │   │   ├── LoginReqDTO.java
    │   │   │   │   ├── RegisterReqDTO.java
    │   │   │   │   ├── UserQueryDTO.java                   # 用户多条件查询 DTO
    │   │   │   │   ├── UserUpdateDTO.java
    │   │   │   │   ├── AuditLogQueryDTO.java               # 审计日志多条件查询 DTO
    │   │   │   │   └── ModelConfigDTO.java
    │   │   │   ├── vo/
    │   │   │   │   ├── LoginRespVO.java
    │   │   │   │   ├── SysUserVO.java
    │   │   │   │   └── BigScreenStatsVO.java               # 管理员监控大屏聚合数据 VO
    │   │   │   ├── service/
    │   │   │   │   ├── SysAuthService.java                 # 登录、注册、BCrypt加密、安全退出
    │   │   │   │   ├── SysUserService.java                 # 用户CRUD、多条件查询、算力配额扣减与充值
    │   │   │   │   └── SysAuditAndConfigService.java       # 模型通道配置、敏感词库、审计日志与大屏统计
    │   │   │   └── controller/
    │   │   │       ├── AuthController.java                 # /api/v1/auth/**
    │   │   │       └── AdminSystemController.java          # /api/v1/admin/**
    │   │   │
    │   │   ├── workflow/                                   # 【子系统一：工作流编排与协同 - 负责人：顾峰鸣】
    │   │   │   ├── entity/
    │   │   │   │   ├── WfWorkflow.java                     # 表3: wf_workflow 主表实体
    │   │   │   │   └── WfNodeEdge.java                     # 表4: wf_node_edge 从表实体
    │   │   │   ├── mapper/
    │   │   │   │   ├── WfWorkflowMapper.java
    │   │   │   │   └── WfNodeEdgeMapper.java
    │   │   │   ├── dto/
    │   │   │   │   ├── WorkflowQueryDTO.java               # 工作流多条件查询 DTO
    │   │   │   │   ├── WorkflowCreateDTO.java
    │   │   │   │   └── CanvasSaveDTO.java                  # 画布节点与连线主从保存 DTO
    │   │   │   ├── vo/
    │   │   │   │   ├── WorkflowSummaryVO.java
    │   │   │   │   ├── WorkflowDetailVO.java               # 主从表聚合详情 VO
    │   │   │   │   └── WorkspaceDashboardVO.java
    │   │   │   ├── websocket/
    │   │   │   │   ├── CollabWebSocketHandler.java         # 多人实时同屏光标与Yjs增量广播处理器
    │   │   │   │   └── CollabMessage.java                  # 协同消息帧结构
    │   │   │   ├── service/
    │   │   │   │   └── WfWorkflowService.java              # 工作流主从CRUD、版本快照回滚、协作成员管理
    │   │   │   └── controller/
    │   │   │       └── WfWorkflowController.java           # /api/v1/workflows/**
    │   │   │
    │   │   ├── engine/                                     # 【子系统二：AI执行引擎与模板生态 - 负责人：张誉诚】
    │   │   │   ├── entity/
    │   │   │   │   ├── WfExecRecord.java                   # 表5: wf_exec_record 主表实体
    │   │   │   │   └── WfExecNodeLog.java                  # 表6: wf_exec_node_log 从表实体
    │   │   │   ├── mapper/
    │   │   │   │   ├── WfExecRecordMapper.java
    │   │   │   │   └── WfExecNodeLogMapper.java
    │   │   │   ├── dto/
    │   │   │   │   ├── WorkflowExecuteReqDTO.java
    │   │   │   │   ├── TextToFlowReqDTO.java
    │   │   │   │   ├── ExecRecordQueryDTO.java             # 运行记录多条件查询 DTO
    │   │   │   │   └── TemplateMarketQueryDTO.java         # 模板广场多条件查询 DTO
    │   │   │   ├── vo/
    │   │   │   │   └── ExecRecordDetailVO.java             # 运行记录主从详情 VO
    │   │   │   ├── runtime/
    │   │   │   │   ├── DagTopologicalSorter.java           # Kahn 有向无环图拓扑排序与环路检测器
    │   │   │   │   ├── WorkflowDagExecutor.java            # 工作流核心执行器 (支持SSE流式推送与同步执行)
    │   │   │   │   └── LlmClientAdapter.java               # DeepSeek/Qwen真实调用与本地智能流式降级适配器
    │   │   │   ├── service/
    │   │   │   │   ├── WfEngineService.java                # 沙箱执行、Text-to-Flow、运行记录查询
    │   │   │   │   └── TemplateMarketService.java          # 模板广场多条件检索、点赞、一键 Fork
    │   │   │   └── controller/
    │   │   │       ├── WfEngineController.java             # /api/v1/engine/**
    │   │   │       └── TemplateMarketController.java       # /api/v1/market/templates/**
    │   │   │
    │   │   └── edu/                                        # 【子系统三：实训教务与评测分析 - 负责人：郝唐鑫】
    │   │       ├── entity/
    │   │       │   ├── EduTask.java                        # 表7: edu_task 主表实体
    │   │       │   └── EduSubmission.java                  # 表8: edu_submission 从表实体
    │   │       ├── mapper/
    │   │       │   ├── EduTaskMapper.java
    │   │       │   └── EduSubmissionMapper.java
    │   │       ├── dto/
    │   │       │   ├── EduTaskQueryDTO.java
    │   │       │   ├── EduTaskSaveDTO.java
    │   │       │   ├── SubmissionSubmitDTO.java
    │   │       │   ├── SubmissionQueryDTO.java             # 作业批改多条件查询 DTO
    │   │       │   └── SubmissionGradeDTO.java
    │   │       ├── vo/
    │   │       │   ├── EduTaskVO.java
    │   │       │   ├── EduSubmissionVO.java
    │   │       │   └── EduAnalyticsVO.java                 # 教师端班级学情统计 ECharts VO
    │   │       ├── service/
    │   │       │   └── EduTrainingService.java             # 课题发布、作业快照校验、批改打分、学情统计
    │   │       └── controller/
    │   │           └── EduTrainingController.java          # /api/v1/edu/**
    │   │
    │   └── resources/
    │       ├── application.yml                             # 主配置文件 (端口、MySQL、Redis、JWT、AI通道默认配置)
    │       └── application-test.yml                        # 测试环境配置 (H2/MySQL兼容与内存缓存)
    └── test/
        └── java/com/flowmind/platform/                     # 各模块单元测试与集成测试类
```

---

## 3. 前端工程目录树 (`flowmind-frontend/`)

前端严格包含 **24 个独立视图文件（`.vue`）**，按 4 位成员每人 6 个视图精准划分目录：

```text
flowmind-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
└── src/
    ├── main.ts                                             # 应用入口 (挂载 Pinia, VueRouter, ElementPlus)
    ├── App.vue
    ├── utils/
    │   ├── request.ts                                      # Axios 封装 (自动注入 JWT Header, 拦截 401/403/40009)
    │   ├── sseClient.ts                                    # SSE 流式请求封装
    │   └── collabSocket.ts                                 # WebSocket + Yjs 多人实时光标与画布同步客户端
    ├── stores/
    │   ├── authStore.ts                                    # 当前登录用户、角色、Token与安全退出状态
    │   └── canvasStore.ts                                  # 当前画布节点、连线、在线协作者光标状态
    ├── router/
    │   └── index.ts                                        # 24个视图路由表 + 角色权限路由守卫
    ├── layouts/
    │   └── MainLayout.vue                                  # 顶部导航 + 角色动态侧边栏 + 算力余额徽章
    ├── api/
    │   ├── authAndAdminApi.ts                              # 李俊豪模块 API 封装
    │   ├── workflowApi.ts                                  # 顾峰鸣模块 API 封装
    │   ├── engineAndMarketApi.ts                           # 张誉诚模块 API 封装
    │   └── eduApi.ts                                       # 郝唐鑫模块 API 封装
    └── views/                                              # 【24个独立视图文件 (满足 ≥20 个硬性要求)】
        ├── auth/                                           # [李俊豪负责 - 2个]
        │   ├── LoginView.vue                               # VIEW-01: 多角色统一登录页
        │   └── RegisterView.vue                            # VIEW-02: 师生账号注册页
        ├── error/                                          # [李俊豪负责 - 1个]
        │   └── Error403View.vue                            # VIEW-03: 权限不足拦截与异常降级页
        ├── workspace/                                      # [顾峰鸣负责 - 1个]
        │   └── DashboardView.vue                           # VIEW-04: 个人工作台概览页
        ├── workflow/                                       # [顾峰鸣负责 - 5个]
        │   ├── WorkflowListView.vue                        # VIEW-05: 我的/协作工作流管理页 (多条件查询)
        │   ├── CanvasEditorView.vue                        # VIEW-06: Vue Flow 可视化画布与多人实时光标页
        │   ├── NodeConfigDrawer.vue                        # VIEW-07: 节点配置与 Monaco 代码/提示词编辑视图
        │   ├── VersionHistoryView.vue                      # VIEW-08: 历史版本快照对比与一键回滚视图
        │   └── CollabManageModal.vue                       # VIEW-09: 协作成员邀请与在线状态管理视图
        ├── engine/                                         # [张誉诚负责 - 4个]
        │   ├── SandboxRunnerView.vue                       # VIEW-10: SSE 流式运行调试沙箱与节点流光追踪视图
        │   ├── TextToFlowView.vue                          # VIEW-11: AI 自然语言一句话生成工作流视图
        │   ├── ExecRecordListView.vue                      # VIEW-12: 运行历史记录主表多条件查询页
        │   └── ExecNodeDetailView.vue                      # VIEW-13: 节点级执行明细从表与耗时瀑布图视图
        ├── market/                                         # [张誉诚负责 - 2个]
        │   ├── TemplateMarketView.vue                      # VIEW-14: 模板广场多条件检索页
        │   └── TemplateDetailView.vue                      # VIEW-15: 模板画布预览与一键 Fork 详情页
        ├── edu/                                            # [郝唐鑫负责 - 6个]
        │   ├── StudentTaskListView.vue                     # VIEW-16: 学生端我的实训课程任务列表页
        │   ├── SubmissionEditView.vue                      # VIEW-17: 学生端实训作业快照绑定与报告提交页
        │   ├── TeacherTaskManageView.vue                   # VIEW-18: 教师端实训课题发布与规则配置页 (主表CRUD)
        │   ├── TeacherGradeListView.vue                    # VIEW-19: 教师端学生作业多条件筛选列表页
        │   ├── TeacherCanvasReviewView.vue                 # VIEW-20: 教师端在线加载学生画布回放试跑与批改页
        │   └── EduAnalyticsView.vue                        # VIEW-21: 教师端班级学情 ECharts 统计分析页
        └── admin/                                          # [李俊豪负责 - 3个]
            ├── BigScreenMonitorView.vue                    # VIEW-22: 管理员全校算力与实训实时监控大屏页
            ├── UserAndRbacView.vue                         # VIEW-23: 师生用户多条件查询、角色与算力配额管理页
            └── ModelAndAuditLogView.vue                    # VIEW-24: 大模型通道配置、敏感词维护与安全审计查询页
```

---

## 4. 4 人团队文件所有权边界表（防止并行开发冲突）

| 团队成员 | 独占负责的后端包路径 | 独占负责的前端视图与 API 文件 | 共享只读基础层 |
| :--- | :--- | :--- | :--- |
| **顾峰鸣 (组长)** | `com.flowmind.platform.workflow.**`<br>`com.flowmind.platform.common.**` (维护人) | `src/views/workspace/**`<br>`src/views/workflow/**`<br>`src/api/workflowApi.ts`<br>`src/utils/collabSocket.ts` | `common/api/Result.java`<br>`common/security/SecurityUtils.java` |
| **张誉诚** | `com.flowmind.platform.engine.**` | `src/views/engine/**`<br>`src/views/market/**`<br>`src/api/engineAndMarketApi.ts`<br>`src/utils/sseClient.ts` | 同上 |
| **郝唐鑫** | `com.flowmind.platform.edu.**` | `src/views/edu/**`<br>`src/api/eduApi.ts` | 同上 |
| **李俊豪** | `com.flowmind.platform.system.**` | `src/views/auth/**`<br>`src/views/error/**`<br>`src/views/admin/**`<br>`src/api/authAndAdminApi.ts` | 同上 |
