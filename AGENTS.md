# AGENTS.md — FlowMind 项目 Codex 最高开发规范

> **重要指令**：任何 AI 编程智能体（OpenAI Codex 等）在进入本仓库编写、修改或重构任何代码前，**必须首先完整阅读本文件及 `docs/` 目录下的关联文档**。本文件中的规则具有最高优先级。

---

## 1. 必读文档索引（写代码前必须查阅）

在执行具体开发任务（`TASK-001` ~ `TASK-028`）前，你必须按需查阅以下权威设计文档：
1. **任务范围与分工**：`docs/DEVELOPMENT_TASKS.md`（明确每个 `TASK` 的负责人、文件清单与验收标准）
2. **目录树与类名规范**：`docs/PROJECT_STRUCTURE.md`（前后端所有包名、类名、24 个 `.vue` 视图文件的唯一合法路径）
3. **数据库表结构**：`docs/DATABASE.md` 及 `sql/init_flowmind.sql`（8 张表、字段名、类型、外键与 3 对主从关系）
4. **接口契约**：`docs/API.md`（URL、HTTP 方法、DTO 字段、返回 JSON、错误码与权限要求）
5. **核心算法与业务逻辑**：`docs/BUSINESS_LOGIC.md`（Kahn 拓扑排序、6 类节点执行器、AI 双模适配器、WebSocket 协同协议、实训快照预检）
6. **测试与验收标准**：`docs/TESTING.md`（必须通过的单元测试与集成测试用例）

---

## 2. 十条强制开发铁律（Codex 严禁违反）

1. **严禁擅自更改技术栈**：
   - 后端固定为：**JDK 21 + Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + Spring Security 6 + JJWT + MySQL 8.0 + Redis（含内存降级）**。
   - 前端固定为：**Vue 3.5 + TypeScript + Vite 5 + Pinia + Vue Router 4 + Element Plus + `@vue-flow/core` + `yjs` + `echarts` 5**。
2. **严禁篡改数据库表名、字段名与 API 契约**：
   - 8 张表（`sys_user`、`sys_model_audit_log`、`wf_workflow`、`wf_node_edge`、`wf_exec_record`、`wf_exec_node_log`、`edu_task`、`edu_submission`）的字段命名、主外键关系必须与 `docs/DATABASE.md` 100% 一致。
   - 所有接口返回值必须使用 `Result<T>` 或 `PageResult<T>` 包装，错误码必须使用 `ErrorCode` 枚举。
3. **严禁使用空函数、TODO 或硬编码假数据冒充功能**：
   - 所有的增删改查、多条件分页查询（`LambdaQueryWrapper`）、主从表事务级联操作、ECharts 统计聚合查询必须编写完整的真实数据库操作代码。
4. **严禁破坏 24 个视图文件的数量与路径约定**：
   - `flowmind-frontend/src/views/` 目录下必须严格按照 `docs/PROJECT_STRUCTURE.md` 组织全部 **24 个独立 `.vue` 视图文件**，不得合并或删减视图文件。
5. **严禁跨模块越界修改其他成员负责的文件**：
   - 执行某个 `TASK-xxx` 时，仅允许修改该任务所属子系统的后端包与前端视图目录。
   - 四个业务子系统包路径严格隔离：`system`（李俊豪）、`workflow`（顾峰鸣）、`engine`（张誉诚）、`edu`（郝唐鑫）。
6. **必须完整实现课程五大必查系统级能力**：
   - **密码编码器**：必须使用 `BCryptPasswordEncoder` 对密码加盐哈希。
   - **过滤器链**：必须实现 `CorsFilter`、`SensitiveAndXssFilter`（拦截违规敏感词并记录审计表）、`JwtAuthenticationFilter`。
   - **全局异常处理**：必须通过 `@RestControllerAdvice` (`GlobalExceptionHandler`) 统一拦截并格式化所有异常。
   - **安全退出**：`/api/v1/auth/logout` 必须将当前 Token 的 `jti` 写入 `TokenCacheService` 黑名单，并在后续请求中拒绝该 Token。
   - **统计分析**：教师端 `EduAnalyticsView.vue` 与管理员端 `BigScreenMonitorView.vue` 必须渲染真实的 ECharts 多维图表。
7. **必须保障离线与测试环境 100% 可运行（双模容错）**：
   - `TokenCacheService` 必须支持在无外部 Redis 时自动回退至 `ConcurrentHashMap` 内存黑名单。
   - `LlmClientAdapter` 必须支持在未配置外部 API Key 时自动启用内置智能流式沙箱模拟器，保证 SSE 流式输出、节点高亮与自动化测试在任何环境下 100% 成功。
8. **严禁随意引入未声明的重量级第三方依赖**：
   - 不得引入 Kafka、RabbitMQ、Nacos、Elasticsearch 等外部中间件依赖，保持单机开箱即用。
9. **每完成一个任务必须执行编译与测试验证**：
   - 后端修改后必须执行 `mvn clean test` 确保编译与单元测试通过；
   - 前端修改后必须执行 `npm run build` 确保 TypeScript 类型检查与打包通过。
10. **发现文档冲突或未决事项时的处理原则**：
    - 若发现细节疑问，优先参考 `FINAL_REVIEW.md` 中的预设默认方案，并在任务完成报告中明确向项目组长汇报，不得擅自推翻既定架构。

---

## 3. 代码风格与质量要求

- **Java 后端**：使用 `@Slf4j` 记录关键日志；涉及主从表同时写入/更新/删除的 Service 方法必须添加 `@Transactional(rollbackFor = Exception.class)`；DTO 入参必须添加 Jakarta Validation 校验注解（`@NotBlank`, `@NotNull` 等）。
- **Vue 3 前端**：统一使用 `<script setup lang="ts">` 语法；所有 API 调用统一收敛在 `src/api/*.ts` 中；每个管理列表页必须包含完整的多条件搜索栏、分页器、加载状态与空状态提示。
