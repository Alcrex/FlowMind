# FlowMind 集成修复与验收报告

验收日期：2026-10-31。范围：模型配置实际生效、浏览器验收服务启动、真实 MySQL 验证及交付文档同步。本报告记录本次代码的实际验证结果，不替代原始需求和架构规范。

## 1. 已解决的问题

| 问题 | 修复后的行为 |
| --- | --- |
| 管理员模型设置仅保存，执行器仍只读环境变量 | 执行器读取管理员已保存的完整配置，实际使用通道、URL、解密后的 Key、模型名称与降级开关；每次调用读取一次一致的配置快照，无需重启 |
| 浏览器服务重复注册 `/api/v1/auth/me`，导致启动冲突 | 移除测试替身 Controller，使用生产认证接口；脚本优先使用 JAVA_HOME 的 Java，Playwright 自动启动和关闭隔离后端与 Vite |
| 浏览器测试伪造 JWT，业务闭环及真实 MySQL 验证不足 | 改用真实登录；补充管理员、学生和教师业务验收；新增临时独立 MySQL 实例的自动化验收脚本和测试 |
| README 和模块交付说明仍描述早期未集成状态 | 更新启动、配置优先级、测试方式及模块状态；保留历史任务报告并指向本报告 |

模型配置采用以下明确规则：管理员保存的配置具有全局优先级；尚未保存时，继续使用节点通道与环境配置。初始化 SQL 中描述性的 MODEL_CONFIG 审计记录不视为管理员覆盖配置。无 Key 或选择 MOCK_LOCAL 时始终启用本地流式沙箱，满足离线要求；有 Key 且禁用降级时远端失败必须失败。已输出部分远端内容后不会重放本地回答。此处落实既有双模要求，未更改 API、表结构或技术栈。

## 2. 实测结果

| 验证 | 结果 | 说明 |
| --- | --- | --- |
| `mvn clean test -B` | BUILD SUCCESS | 总计 67 项，65 通过、0 失败、0 错误、2 跳过；跳过的仅为需单独启用的 MySQL 用例 |
| 临时 MySQL 验收脚本 | 2 通过，0 失败/错误/跳过 | MySQL 8.0.42，执行权威初始化 SQL；保留生产 JSON、索引、外键和种子数据 |
| `npm run test:e2e` | 5 组全部通过 | Microsoft Edge，无重试；完整套件耗时 44.4 秒，使用真实认证及隔离 H2 数据库 |
| `npm run build` | 退出码 0 | Vue/TypeScript 检查和 Vite 生产打包成功 |
| 测试文件与 Playwright 配置的独立 tsc 检查 | 退出码 0 | 覆盖默认前端生产构建之外的测试 TypeScript |
| 视图清单 | 恰好 24 个 `.vue` 文件 | 路径符合 PROJECT_STRUCTURE，未合并或删减视图 |

后端回归包含本次新增的管理员配置到真实 HTTP SSE 传输测试：本机服务核验请求路径、Authorization、模型名称、usage 和审计通道；同一进程切换 DeepSeek/Qwen/MOCK_LOCAL，并验证远端 503 时降级开关和无 Key 离线行为。

MySQL 两项用例验证 8 张表、11 个 JSON 字段和 4 个 ON DELETE CASCADE 外键，以及真实登录与越权、JSON_CONTAINS 协作查询、画布主从保存/回滚、同步与 SSE 执行、模板 Fork、学生提交、原画布回滚后的冻结快照回放、95 分评分、教务/管理员聚合统计、级联删除和退出黑名单。

## 3. 浏览器视图覆盖

下表表示实际访问和主要交互覆盖，不表示所有界面输入组合均穷举。

| 视图（均位于 flowmind-frontend/src/views/） | 验证场景 |
| --- | --- |
| auth/LoginView.vue、auth/RegisterView.vue、error/Error403View.vue | 注册校验、注册、三角色真实登录、学生访问管理员页被拦截 |
| workspace/DashboardView.vue、workflow/WorkflowListView.vue | 工作台真实列表；创建、设置修改、筛选、克隆、删除、空状态 |
| workflow/CanvasEditorView.vue、workflow/VersionHistoryView.vue | 双窗口 Yjs、节点连线、拖拽、光标、并发新增、版本保存及回滚 |
| workflow/NodeConfigDrawer.vue、workflow/CollabManageModal.vue | 独立路由配置保存并返回编辑器，节点配置跨窗口同步 |
| engine/TextToFlowView.vue、engine/SandboxRunnerView.vue | 自然语言生成 5 节点工作流、输入初始化完成后运行、SSE 成功输出 |
| engine/ExecRecordListView.vue、engine/ExecNodeDetailView.vue | 真实运行记录、5 条节点日志、耗时图初始化 |
| market/TemplateMarketView.vue、market/TemplateDetailView.vue | 关键词筛选、5 节点预览、点赞、Fork 后校验复制节点 |
| edu/TeacherTaskManageView.vue、edu/StudentTaskListView.vue、edu/SubmissionEditView.vue | 教师发布课题、学生查找课题、选择工作流、预检并提交报告 |
| edu/TeacherGradeListView.vue、edu/TeacherCanvasReviewView.vue | 选择作业；原画布回滚后仍回放 3 节点快照；分项评分合计 95；学生查看反馈 |
| edu/EduAnalyticsView.vue | 4 张 ECharts 初始化、实际平均成绩为 95 |
| admin/UserAndRbacView.vue、admin/ModelAndAuditLogView.vue、admin/BigScreenMonitorView.vue | 用户筛选与管理弹窗、模型配置保存读回与审计、3 张 ECharts、安全退出后旧 Token 401 |

## 4. 复现命令

Windows 环境使用 JDK 21、Maven、Node/npm、Microsoft Edge；先按 README 安装工程依赖。浏览器验收要求 8080 和 5173 端口空闲，拒绝复用其他正在运行的服务。

```powershell
cd D:\FlowMind\flowmind-backend
mvn clean test -B
./scripts/test_mysql.ps1 -MySqlBin 'D:\MYSQL\MySQL Server 8.0\bin'

cd D:\FlowMind\flowmind-frontend
npm run build
$taskTestFiles = @(rg --files tests -g '*.ts')
& ./node_modules/.bin/tsc.cmd --noEmit --target ES2022 --module ESNext --moduleResolution Bundler --types node --skipLibCheck @taskTestFiles playwright.config.ts
npm run test:e2e
```

MySQL 脚本使用临时目录、随机本机端口和 `flowmind_acceptance_<UUID>` 数据库；不连接现有业务服务或重建 `flowmind_db`。测试完成后关闭自己启动的实例，恢复进程环境变量并清理该实例的数据目录。测试类拒绝非 MySQL、非随机验收库名或非空库，默认测试不会自动启动 MySQL。

后端详细结果在 `flowmind-backend/target/surefire-reports/`，后续执行同名测试或 clean 会覆盖或删除对应报告。浏览器失败时在 `flowmind-frontend/test-results/` 生成截图和上下文。

## 5. 验证边界

- 真实 MySQL 和真实浏览器分别运行：浏览器使用 H2 测试库，MySQL 用例使用生产 Controller/Service 和 MockMvc；本次未将浏览器直接连接生产数据库。
- 未使用付费外部模型账号。远端协议通过本机 HTTP SSE 服务验证，真实厂商账号的网络、授权及计费仍需部署环境验证。
- Redis 不可用时的内存降级由现有后端回归验证；本次未单独部署真实 Redis 集群。
- 构建仍有 Element Plus/ECharts 分块大于 500 kB 的体积提示；浏览器页面切换时日志曾出现 WebSocket 代理断连提示，五组场景均通过且无页面脚本异常。本次不据此承诺公网负载和延迟指标。
- 本次完成上述缺陷修复及集成验收；尚未执行生产部署或上线后的容量与长期稳定性测试。
