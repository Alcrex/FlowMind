# TASK-001～TASK-007 交付报告

日期：2026-10-31；负责人：顾峰鸣（Alcrex）；交付分支：`main`。

## 已实现范围

| 任务 | 实现 |
| --- | --- |
| TASK-001 | JDK 21 / Spring Boot 3.3.5 / MyBatis-Plus 3.5.7 Maven 工程；Result、PageResult、完整 ErrorCode、分页插件和 OpenAPI。保留并核验现有 MySQL 初始化 SQL 的 8 张表、外键与种子数据。 |
| TASK-002 | BusinessException、全局异常与校验处理、CORS 白名单及预检、WebSocket 端点注册。 |
| TASK-003 | 两张工作流表的完整实体和 Mapper；LambdaQueryWrapper 多条件分页；默认首尾节点；真实数据库统计；主从事务保存/删除；快照与回滚；成员管理；完整画布克隆。 |
| TASK-004 | 握手 JWT/黑名单/账户状态/工作流权限校验；在线成员和彩色光标；房间隔离；服务端覆盖发送者身份；Yjs 增量与新成员状态同步；撤销权限断开连接；回滚后重建协同状态。 |
| TASK-005 | Vue 3.5 / TypeScript / Vite 5 / Pinia / Vue Router 4 / Element Plus / Vue Flow / Yjs / ECharts 5；主布局；Bearer Token 请求封装；角色路由守卫；全部 24 个视图的路由接入表。 |
| TASK-006 | 工作台真实统计和 ECharts 分类图；多条件列表、分页、新建/克隆/删除；版本元素与参数差异对比、回滚；协作成员添加/移除。 |
| TASK-007 | 六类节点添加/拖入/连线/删除；参数抽屉与代码、Prompt、JSON 编辑；40ms 光标节流；Yjs 按元素及字段同步；并发新增、断线重连、未保存提醒和只读预览。 |

SQL 中 `admin.real_name = 顾峰鸣(管理员)`，`teacher_litian.real_name = 李甜`；六个演示账号的 BCrypt 哈希均通过密码 `123456` 的校验。

主从写入、克隆、回滚、删除均由 Spring 事务控制。写操作锁定主表行；从表插入失败时整笔事务回滚。协作者可以编辑及回滚，只有创建者可以修改公开状态、管理成员和删除。私有画布的教师读取权限通过作业对应课题的教师 ID 校验。

## 核心文件

- 后端：`flowmind-backend/pom.xml`、`src/main/resources/application.yml`、`src/main/java/com/flowmind/platform/common/**`、`workflow/**`。
- 自动化：`flowmind-backend/src/test/java/com/flowmind/platform/workflow/WorkflowCrudAndCollabTest.java`，`src/test/resources/application-test.yml` 与 `schema-test.sql`。
- 前端公共层：`flowmind-frontend/src/{api,router,stores,utils,layouts,components}/`。
- 六个独立视图：`views/workspace/DashboardView.vue`，`views/workflow/{WorkflowListView,CanvasEditorView,NodeConfigDrawer,VersionHistoryView,CollabManageModal}.vue`。
- 浏览器测试：`flowmind-frontend/tests/workflow.spec.ts`、`playwright.config.ts`。

## 验证

- `mvn clean test`：8 项工作流集成测试，覆盖主从创建/保存/删除、组合分页、快照回滚、完整克隆、写入失败回滚、非法画布、权限、CORS、黑名单、数据库统计、种子 BCrypt 和真实 WebSocket 身份/广播/房间隔离。
- `mvn compile`：JDK 21 编译。
- `npm run build`：`vue-tsc --noEmit` 类型检查和 Vite 生产打包。
- `npm run test:e2e`：2 条真实 Edge 浏览器测试，使用独立浏览器上下文与真实工作流接口。覆盖节点与连线同步、配置、拖动、光标、并发新增、成员变更保留未保存内容、快照/回滚、列表筛选、新建/改名/克隆/删除。

后端测试使用 H2 MySQL 模式，不依赖运行中的 MySQL、Redis或外部 AI。H2 不支持原始 MySQL 脚本的建库、索引选项和 JSON_CONTAINS；`scripts/prepare_test_schema.py` 从权威 SQL 生成测试适配脚本，测试专用函数实现 JSON 数组成员查询。生产 SQL 及字段不变。本批次没有在真实 MySQL 上执行破坏性的初始化脚本，也没有验证尚未实现的其他子系统。

Vite 会提示 Element Plus 与 ECharts 的完整依赖包体积超过 500KB；构建可正常通过，后续可在全模块接入后统一做组件按需加载。

## 本地启动与复现

生产业务运行需要 MySQL 8.0，先按既有说明初始化 `sql/init_flowmind.sql`，设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。设置 `JAVA_HOME` 为 JDK 21，并将其 `bin` 放到 PATH 前部。

```powershell
cd flowmind-backend
mvn spring-boot:run
```

另一个终端：

```powershell
cd flowmind-frontend
npm ci
npm run dev
```

前端地址 `http://127.0.0.1:5173`，代理后端 `8080`，接口文档 `/doc.html`。`JWT_SECRET` 可通过环境变量配置。设置 `REDIS_ENABLED=true` 启用 Redis；连接失败会回退本机黑名单。

无需外部服务的浏览器测试复现：先在后端目录运行以下命令，保持测试服务器运行，再在前端目录运行 `npm run test:e2e`。Playwright 默认使用已安装的 Microsoft Edge；其他机器可在配置中调整 channel。测试结束后关闭测试服务器。

```powershell
python scripts/prepare_test_schema.py
./scripts/start_browser_test_server.ps1
```

浏览器服务器只存在于 `src/test` 中，启用 `test,browser-test` 配置；其 `/auth/me` 通过已验证 JWT 查询真实 H2 用户，作为尚未接入的认证模块的测试边界。它不会被打包进生产应用，也不提供演示登录或绕过鉴权。

## 集成边界与文档细节

1. `TASK-003` 文档依赖 `TASK-009`，但仓库尚无 system 模块。本批次在公共层提供 JWT、BCrypt、Redis/内存黑名单和安全链衔接；登录/注册/安全退出 Controller、敏感词审计及认证页面仍属于李俊豪的后续任务。生产环境中的交互登录和 `/auth/me` 需该模块接入后才能完成；不能把本批次构建通过等同于全系统已可登录。
2. 当前只实现分工要求的 6 个视图，原路径保持不变；其余 18 个视图由对应成员补齐，未创建空视图冒充业务。路由通过 `import.meta.glob` 自动接入后续视图；未接入页面明确显示模块状态。因此全仓库 24 个视图齐全的 TASK-028 验收尚未达成。
3. TASK-006 要求个人工作流“克隆”，但 API.md 只声明了由 engine 模块负责的公开模板 `/fork`。为实现私有工作流的事务克隆，新增兼容接口 `POST /api/v1/workflows/{id}/clone`，返回 `Result<WorkflowDetailVO>`；原接口未改动，也未占用模板模块。
4. BUSINESS_LOGIC.md 的在线帧为 `PRESENCE_SYNC`，ARCHITECTURE.md 同时提到 JOIN/LEAVE；实际进出统一广播 `PRESENCE_SYNC`。光标严格按 `userId % 6` 分配颜色（用户 3 为粉色），遵循算法而非 TESTING.md 示例中的蓝色描述。
5. 房间按 session 保存连接，以支持同一账号打开多个窗口；广播排除发送 session。增加 `CONNECTED`、`GRAPH_INIT`、`SYNC_REQUEST`、`MEMBERS_CHANGED`、`WORKFLOW_REFRESH` 控制帧，用于首次同步和版本失效；原 CURSOR_MOVE/GRAPH_DELTA/YJS_UPDATE 帧保留。
6. 版本快照记录版本号、保存人、保存时间及完整节点/连线，支持额外保存名称/描述。创建时保存 v1.0.0；新版本号必须唯一。普通保存更新当前画布；未保存的协同状态不视为历史版本。已有作业引用的工作流遵循既定外键 RESTRICT，删除冲突返回规范错误。
