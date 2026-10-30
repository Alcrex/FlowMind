# FlowMind（智流）

**支持多人实时协同的 AI 工作流可视化编排与实训平台**

2026—2027 学年第一学期《Web 应用综合实训》课程项目。

FlowMind 面向高校人工智能与软件工程实践教学，计划将工作流拖拽编排、多人画布协同、AI 流式执行与实训教务管理整合到同一个 Web 平台中，覆盖“教师发布课题 → 学生协同编排 → 提交画布快照与报告 → 教师试跑批改 → 班级学情分析”的完整流程。

> **当前进度：需求与设计阶段。** 仓库已包含需求、架构、接口、数据库、任务分工与测试计划等文档，以及数据库初始化脚本。`flowmind-backend/` 与 `flowmind-frontend/` 尚未创建；下文的业务功能、工程目录及启动方式均为开发目标或后续操作说明。

## 1. 项目目标

- **可视化编排**：通过 Vue Flow 拖拽节点、连接依赖关系、编辑提示词与节点参数，并保存、克隆及回滚工作流。
- **多人实时协同**：通过 WebSocket 与 Yjs 同步画布变更、在线成员及彩色光标。
- **AI 工作流运行**：使用 Kahn 拓扑排序执行有向无环图（DAG），通过 SSE 展示节点状态、执行日志与流式输出。
- **实训教学闭环**：支持课题发布、必含节点预检、作业快照提交、教师在线试跑、分项评分与学情统计。
- **权限与资源治理**：提供三类角色权限、Token 算力配额、模型通道配置、敏感词过滤、安全审计及监控大屏。

项目设计规模为 **4 个业务子系统、3 类角色、8 张核心数据表、3 对主从表、24 个独立前端视图、28 项开发任务**。

## 2. 用户角色与功能规划

| 角色 | 角色标识 | 主要功能 |
| --- | --- | --- |
| 学生 / 创作者 | `STUDENT` | 个人工作台、工作流管理、协同编排、运行调试、模板复用、实训作业提交 |
| 指导教师 | `TEACHER` | 发布课题、配置节点规则与评分标准、筛选作业、快照试跑批改、班级学情分析 |
| 系统管理员 | `ADMIN` | 用户与角色管理、算力配额管理、模型通道配置、敏感词维护、安全审计与全局监控 |

工作流包含以下 6 类标准节点：

| 节点类型 | 用途 |
| --- | --- |
| `INPUT` | 接收运行输入、合并默认变量 |
| `KNOWLEDGE` | 从内置知识库检索相关规范与参考文本 |
| `CODE` | 执行预定义的安全文本处理、提取及格式转换规则 |
| `CONDITION` | 根据条件选择执行分支 |
| `LLM` | 调用大模型或本地流式沙箱，生成处理结果 |
| `OUTPUT` | 汇总并输出 Markdown 或 JSON 结果 |

## 3. 技术栈与环境要求

| 层次 | 技术选型 | 环境要求 |
| --- | --- | --- |
| 后端 | Spring Boot 3.3.5、MyBatis-Plus 3.5.7、Spring Security 6、JJWT | JDK 21、Maven 3.9+ |
| 前端 | Vue 3.5、TypeScript、Vite 5、Pinia、Vue Router 4、Element Plus | Node.js 20+、npm 10+ |
| 画布与协同 | `@vue-flow/core`、Yjs、WebSocket、Monaco Editor | 现代浏览器 |
| 流式通信与图表 | SSE、`@microsoft/fetch-event-source`、ECharts 5 | 随前后端工程安装 |
| 数据库 | MySQL | MySQL 8.0+ |
| 缓存 | Redis，支持内存降级 | Redis 7.x（可选） |
| AI 通道 | DeepSeek / 通义千问兼容接口、本地流式沙箱 | 外部 API Key（可选） |

### 双模运行设计

- **缓存双模**：`TokenCacheService` 在 Redis 不可用时回退到 `ConcurrentHashMap` 内存黑名单，保留安全退出与令牌拦截能力。
- **AI 双模**：`LlmClientAdapter` 在未配置外部 API Key 或触发降级策略时使用内置流式沙箱，支持离线演示与自动化测试。

离线模式仍需具备本地运行环境和数据库；首次安装 Maven 与 npm 依赖需要网络或预先准备的依赖缓存。

## 4. 团队分工

每位成员负责 2 张数据表、6 个独立视图和 7 项开发任务。

| 成员 | GitHub 账号与专属分支 | 职责与后端模块 | 任务范围 | 负责数据表 | 前端视图目录 |
| --- | --- | --- | --- | --- | --- |
| 顾峰鸣（组长/管理员） | `Alcrex` (`main` 主干维护) | 公共架构底座、工作流编排与实时协同；`workflow`，维护 `common` | `TASK-001`～`TASK-007` | `wf_workflow`、`wf_node_edge` | `workspace/`、`workflow/` |
| 李俊豪 | `lijunhao` (分支: `lijunhao`) | 认证授权、算力治理与监控大屏；`system` | `TASK-008`～`TASK-014` | `sys_user`、`sys_model_audit_log` | `auth/`、`error/`、`admin/` |
| 张誉诚 | `killo.linli` (分支: `killo.linli`) | DAG 执行引擎、流式沙箱与模板生态；`engine` | `TASK-015`～`TASK-021` | `wf_exec_record`、`wf_exec_node_log` | `engine/`、`market/` |
| 郝唐鑫 | `changdi-in` (分支: `changdi-in`) | 实训教务、作业评测与学情统计；`edu` | `TASK-022`～`TASK-028` | `edu_task`、`edu_submission` | `edu/` |

公共安全层按任务分工共建，具体文件归属、依赖关系及验收条件见 [开发任务分解](docs/DEVELOPMENT_TASKS.md) 与 [目录组织规范](docs/PROJECT_STRUCTURE.md)。

## 5. 仓库结构

### 当前主要文件

```text
FlowMind/
├── AGENTS.md                           # 项目开发规范与 AI 编程约束
├── README.md                           # 项目入口说明
├── FINAL_REVIEW.md                     # 架构审查与待确认事项
├── docs/
│   ├── PROJECT_OVERVIEW.md             # 项目概述与课程要求映射
│   ├── SRS.md                          # 软件需求规格说明书
│   ├── ARCHITECTURE.md                 # 系统架构设计
│   ├── DATABASE.md                     # 数据库设计
│   ├── API.md                          # HTTP、SSE 与 WebSocket 接口契约
│   ├── PROJECT_STRUCTURE.md            # 前后端完整目录及文件归属
│   ├── BUSINESS_LOGIC.md               # 核心算法与业务规则
│   ├── DEVELOPMENT_TASKS.md            # 28 项开发任务与验收标准
│   ├── GIT_WORKFLOW.md                 # 分支、提交与代码评审规范
│   ├── TESTING.md                      # 测试计划与验收用例
│   ├── CODEX_GUIDE.md                  # Codex 开发执行指南
│   └── FINAL_REVIEW.md                 # 架构审查报告副本
├── sql/
│   └── init_flowmind.sql               # 建表与演示种子数据脚本
├── 26-27-1Web应用综合实训要求.docx
└── 第一阶段检查报告_选题分工与需求概要设计.md
```

### 后续工程组织

以下目录将在对应开发任务中创建，完整类名与 24 个视图文件路径以 [PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md) 为准。

```text
flowmind-backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/flowmind/platform/
    │   │   ├── FlowMindApplication.java
    │   │   ├── common/                 # 统一响应、安全、异常与公共配置
    │   │   ├── system/                 # 用户、权限、算力与审计
    │   │   ├── workflow/               # 工作流、版本与协同
    │   │   ├── engine/                 # DAG、节点执行与模板
    │   │   └── edu/                    # 课题、作业与统计
    │   └── resources/
    │       ├── application.yml
    │       └── application-test.yml
    └── test/java/com/flowmind/platform/

flowmind-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
└── src/
    ├── main.ts
    ├── App.vue
    ├── api/                            # 按模块封装接口请求
    ├── layouts/                        # 页面布局
    ├── router/                         # 路由与权限守卫
    ├── stores/                         # 登录与画布状态
    ├── utils/                          # 请求、SSE 与协同客户端
    └── views/                          # 严格保留 24 个独立视图
        ├── auth/                       # 2 个
        ├── error/                      # 1 个
        ├── workspace/                  # 1 个
        ├── workflow/                   # 5 个
        ├── engine/                     # 4 个
        ├── market/                     # 2 个
        ├── edu/                        # 6 个
        └── admin/                      # 3 个
```

## 6. 数据库设计

数据库名称为 `flowmind_db`，使用 UTF-8 字符集 `utf8mb4`。

| 数据表 | 用途 |
| --- | --- |
| `sys_user` | 用户信息、角色与 Token 算力账户 |
| `sys_model_audit_log` | 模型配置与安全审计记录 |
| `wf_workflow` | 工作流基本信息与版本快照 |
| `wf_node_edge` | 工作流节点与连线明细 |
| `wf_exec_record` | 工作流运行记录 |
| `wf_exec_node_log` | 节点执行日志 |
| `edu_task` | 教师发布的实训课题 |
| `edu_submission` | 学生作业、提交快照与评测结果 |

3 对主从关系：

- `wf_workflow` → `wf_node_edge`：工作流与节点连线。
- `wf_exec_record` → `wf_exec_node_log`：运行记录与节点日志。
- `edu_task` → `edu_submission`：实训课题与作业提交。

作业提交必须固化 `canvas_snapshot_json`，教师批改与试跑使用提交时的快照，避免学生后续修改画布影响评测。

## 7. 本地准备与启动说明

### 7.1 初始化数据库

在仓库根目录打开终端，连接 MySQL：

```shell
mysql -u root -p
```

进入 MySQL 客户端后执行：

```sql
SOURCE sql/init_flowmind.sql;
```

脚本包含数据库创建、8 张表的重建和演示数据插入。**重复执行会删除并重建同名业务表，清除已有数据，请仅在可重置的开发或演示数据库中使用。**

### 7.2 后端启动（工程创建后）

先在 `flowmind-backend/src/main/resources/application.yml` 中配置数据库连接、JWT 签名参数，以及按需启用的 Redis 与模型通道。数据库用户名与密码以本地环境为准，真实密钥不提交到仓库。

```shell
cd flowmind-backend
mvn spring-boot:run
```

### 7.3 前端启动（工程创建后）

在另一个终端执行：

```shell
cd flowmind-frontend
npm install
npm run dev
```

按设计约定，默认访问地址如下；实际地址以工程配置和启动日志为准。

| 服务 | 规划地址 |
| --- | --- |
| 前端页面 | `http://localhost:5173` |
| 后端 API | `http://localhost:8080/api/v1` |
| Knife4j 接口文档 | `http://localhost:8080/doc.html` |
| 画布协同 WebSocket | `ws://localhost:8080/ws/collab/{workflowId}?token={jwt}` |

### 7.4 演示账号

初始化脚本预置以下账号，脚本注释约定的初始密码统一为 `123456`。账号登录需待认证模块实现后验证。

| 角色 | 用户名 | 显示姓名 |
| --- | --- | --- |
| 管理员 | `admin` | 顾峰鸣（管理员） |
| 教师 | `teacher_litian` | 李甜 |
| 学生 | `gufengming` | 顾峰鸣 |
| 学生 | `zhangyucheng` | 张誉诚 |
| 学生 | `haotangxin` | 郝唐鑫 |
| 学生 | `lijunhao` | 李俊豪 |

## 8. 开发顺序与协作约定

推荐按依赖顺序推进：

1. **公共底座与安全认证**：数据库、后端脚手架、统一响应、异常处理、过滤器链、JWT 与安全退出。
2. **核心业务后端**：工作流主从表与协同、DAG 执行与 SSE、模板复用、实训课题与快照评测。
3. **前端工程与视图**：搭建路由、状态管理及布局，按成员分工实现全部 24 个独立视图。
4. **联调与验收**：验证角色权限、主从事务、双窗口协同、流式输出、快照批改及统计图表。

开发前阅读 [AGENTS.md](AGENTS.md)，并遵守以下约定：

- 技术栈、8 张表的字段及接口契约保持与设计文档一致；API 返回使用 `Result<T>` 或 `PageResult<T>`，错误码使用 `ErrorCode`。
- 按任务限定的文件范围开发；公共文件修改按团队协作规范处理。
- 后端主从表写操作使用事务，DTO 使用 Jakarta Validation；前端使用 `<script setup lang="ts">`，API 请求统一放在 `src/api/`。
- 管理列表包含多条件搜索、分页、加载及空状态；统计图表使用真实数据库聚合结果。
- 从最新 `dev` 创建个人任务分支，通过 Pull Request 合入 `dev`，里程碑验收后再合入 `main`。
- 文档存在冲突或未决事项时，先查阅根目录 [FINAL_REVIEW.md](FINAL_REVIEW.md) 中的既定方案。

分支示例：`feat/gufengming-TASK-003`。

提交信息示例：

```text
feat(workflow): [TASK-003] 实现工作流主从表管理与多条件查询
```

## 9. 测试与验收

以下命令在对应工程创建并安装依赖后执行。

后端修改后：

```shell
cd flowmind-backend
mvn clean test
```

前端修改后：

```shell
cd flowmind-frontend
npm run build
```

验收重点包括：BCrypt 密码校验、角色越权拦截、退出后令牌失效、敏感词拦截与审计、统一异常响应、3 对主从表事务操作、DAG 环路检测与条件分支、SSE 流式输出、协同同步、作业快照隔离及真实统计聚合。

详细用例和测试类要求见 [TESTING.md](docs/TESTING.md)。当前仓库尚无前后端工程，暂不具备执行上述构建与测试的条件。

## 10. 文档导航

| 文档 | 主要内容 |
| --- | --- |
| [开发规范](AGENTS.md) | 强制约束、技术栈、质量要求与必读文档 |
| [项目概述](docs/PROJECT_OVERVIEW.md) | 项目背景、建设目标与课程要求映射 |
| [需求规格](docs/SRS.md) | 角色、功能需求与验收条件 |
| [架构设计](docs/ARCHITECTURE.md) | 分层、模块依赖与通信机制 |
| [目录规范](docs/PROJECT_STRUCTURE.md) | 完整目录树、类名、24 个视图与文件归属 |
| [数据库设计](docs/DATABASE.md) | 数据字典、外键及主从关系 |
| [初始化脚本](sql/init_flowmind.sql) | 建表与演示种子数据 |
| [接口契约](docs/API.md) | URL、方法、DTO、返回结构、错误码与权限 |
| [业务逻辑](docs/BUSINESS_LOGIC.md) | DAG、节点执行、双模适配、协同与快照预检 |
| [任务分解](docs/DEVELOPMENT_TASKS.md) | `TASK-001`～`TASK-028` 的负责人、范围与验收标准 |
| [协作流程](docs/GIT_WORKFLOW.md) | 分支、提交、PR 与代码评审 |
| [测试计划](docs/TESTING.md) | 自动化测试与端到端验收用例 |
| [Codex 指南](docs/CODEX_GUIDE.md) | 开发批次与任务提示词模板 |
| [最终评审](FINAL_REVIEW.md) | 默认方案、设计冲突处理与待确认事项 |
