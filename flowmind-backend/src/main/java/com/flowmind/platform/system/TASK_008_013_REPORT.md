# TASK-008～013 完成报告（李俊豪 / lijunhao）

完成日期：2026-10-31。工作分支：`lijunhao`，从已有的工作流基座快进同步后开发；提交包含共同作者 `lijunhao <qazlijunhao@gmail.com>`，未推送。

## 实现范围

- TASK-008：完整映射 `sys_user`、`sys_model_audit_log` 与 MyBatis-Plus Mapper；复用并验证已有 `JwtUtil`、`LoginUser`、`SecurityUtils`、`TokenAuthenticator`、Redis/内存双模 `TokenCacheService`。
- TASK-009：BCrypt 注册与登录、当前用户、安全退出及 LOGIN/LOGOUT 审计；退出将 JWT 的 jti 按剩余有效期加入黑名单，返回 40102。已有 Token 的权限及账号状态每次从数据库读取，因此角色变更与封禁立即生效。
- 安全过滤：JSON 可重复读取、解析后的 Unicode 字符串和查询参数检查、忽略大小写与连续空白、常见 XSS 拦截，返回 40009 并归属实际操作人。管理员配置请求仅豁免根级 `sensitiveWords` 数组，允许维护被拦截的规则；其他字段仍检查。
- TASK-010：用户关键词/角色/班级/状态/算力区间组合分页；角色、状态、班级、剩余配额与 BCrypt 密码重置；原子条件更新扣减配额，避免并发超扣；审计组合分页；模型配置读写；管理员和教师只读大屏。
- 大屏：从真实用户、工作流、运行记录、EXEC_AI 审计聚合 KPI、班级到角色到模型的算力桑基图、班级活跃排行，以及含当前小时的最近 24 小时模型调用数/平均延迟。班级查询分别计数，避免主表和运行记录联表放大。
- TASK-011/012：新增 `auth/LoginView.vue`、`auth/RegisterView.vue`、`error/Error403View.vue`、`admin/BigScreenMonitorView.vue`、`admin/UserAndRbacView.vue`、`admin/ModelAndAuditLogView.vue` 六个合法路径视图及 `src/api/authAndAdminApi.ts`。沿用已有 authStore、路由权限守卫、Axios 处理与 MainLayout 安全退出。
- 管理列表包含组合搜索、分页、加载/失败/空状态；大屏自动刷新、监听容器尺寸并在离开时释放图表。

## 验证结果

- JDK 21：`cd flowmind-backend; mvn clean test`，**26 项通过、0 失败、0 错误、0 跳过**。其中 `AuthAndSecurityIntegrationTest` 18 项，原有 `WorkflowCrudAndCollabTest` 8 项。
- 安全集成覆盖 BCrypt/重复注册/角色入参校验、三端预置账号登录、登录失败与封禁、JWT 伪造和过期、RBAC 与实时角色变更、安全退出、敏感词/XSS/Unicode 转义/查询参数拦截、JSON 重放、用户与审计组合查询、并发配额扣减、模型配置恢复和 Key 脱敏、大屏聚合、CORS 与全局异常格式。
- 黑名单测试覆盖 Redis 正常访问、Redis 读写异常回退与无效 TTL；无需外部 Redis。真实 Redis 服务未参与本次验证。
- `cd flowmind-frontend; npm run build`：TypeScript 检查与 Vite 打包通过。现有 Element Plus/ECharts 分包仍有大于 500kB 提示，不影响构建。
- 浏览器连接独立 H2 测试服务，实际验证管理员登录、三张大屏图表、用户筛选、李俊豪测试账号充值（49,000 → 99,000）、配置保存和新增审计、顶部退出、学生登录、越权 403 以及注册必填校验；控制台无 error。充值只发生在进程内 H2 数据库，已随测试服务停止释放。

## 方案选择与向组长说明

1. 设计只允许现有 8 张表，未指定独立配置表。模型配置以 `MODEL_CONFIG` 审计事件中的完整快照持久化，沿用表的 `detail_json`，没有新增表或修改字段/API 契约。Key 用 AES-GCM 加密，密钥从服务器 JWT secret 派生；GET/PUT 配置只返回掩码和空明文，审计查询剔除 Key 字段。服务器侧 `runtimeConfig()` 为后续 LlmClientAdapter 提供通道与可用 Key；保持服务器 secret 稳定才能读取此前加密的配置。
2. 按 FINAL_REVIEW 的离线默认策略，初始通道为 `MOCK_LOCAL`、`enableMockFallback=true`；重置密码为文档中的演示默认值 `123456`，用户配额为“剩余可用量”，不是总配额。
3. 公共链路复用现有安全组件。CORS 提升至最高优先级以使 401/403 也携带跨域头；JWT 验证放在敏感请求过滤之前，以便审计记录可信操作人。其余公共 API/异常处理以及 workflow/engine/edu 业务文件未修改。
4. 仓库当前共 **12 个 `.vue` 视图**（原有工作流 6 个 + 本次 6 个）；engine/market/edu 其余 12 个尚未实现，没有越界新增占位视图或宣称全系统 24 视图验收完成。
5. 原有 `WorkflowBrowserServer` 在 `browser-test` profile 中自带 `/auth/me` 测试替身。system 接入后该测试替身与真实 AuthController 路由冲突，应由其负责人移除测试替身后继续旧工作流 E2E 启动脚本。本次浏览器测试使用真实启动类与 `test` profile；不启用 `browser-test`。现有 Maven 工作流回归正常通过。
6. 本次数据库自动化和浏览器验证使用与建表脚本对应的 H2 MySQL 兼容测试库，未使用生产 MySQL 数据；外网模型实际调用属于后续 engine 模块任务。
