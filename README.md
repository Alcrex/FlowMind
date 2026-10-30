# FlowMind（智流）—— 支持多人实时协同的 AI 工作流可视化编排与实训平台

> **FlowMind: A Real-Time Collaborative Visual AI Workflow Orchestration and Training Platform**
> 2026-2027-1《Web应用综合实训》课程项目

---

## 1. 项目简介

**FlowMind（智流）** 是一套面向高校人工智能与软件工程实践教学的全栈 Web 平台。系统创新性地将 **Vue Flow 可视化节点拖拽编排画布**、**基于 WebSocket + Yjs 的多人实时同屏彩色光标协作**、**Kahn 拓扑排序与 SSE 流式 AI 执行引擎**，以及 **高校实训课题发布—快照提交—在线回放批改—班级学情统计分析** 完整融合。

### 核心特性与课程评分指标覆盖
- **三端角色权限隔离（RBAC）**：支持 **学生/创作者 (`STUDENT`)**、**指导教师 (`TEACHER`)**、**系统管理员 (`ADMIN`)** 三类角色，基于 Spring Security + JWT + Vue Router 前置守卫实现细粒度权限控制。
- **8 张核心业务表（含 3 对严格主从表）**：
  1. 主从对 1：`wf_workflow`（工作流主表） $\leftrightarrow$ `wf_node_edge`（画布节点与连线明细从表）
  2. 主从对 2：`wf_exec_record`（工作流运行记录主表） $\leftrightarrow$ `wf_exec_node_log`（单步节点执行日志从表）
  3. 主从对 3：`edu_task`（实训课程任务主表） $\leftrightarrow$ `edu_submission`（学生实训作业提交与评测从表）
  4. 基础与治理表：`sys_user`（多角色用户与算力账户表）、`sys_model_audit_log`（AI 模型配置与安全审计日志表）
- **24 个前端独立视图组件（`.vue`）**：4 人团队每人精准负责 6 个视图组件与 2 张数据表。
- **完备的系统级工程规范**：内置 `BCryptPasswordEncoder` 密码加密、`JwtAuthenticationFilter` + `SensitiveAndXssFilter` 过滤器链、`@RestControllerAdvice` 全局异常处理、基于 Redis/内存黑名单的安全退出机制，以及基于 Apache ECharts 5 的教师学情分析与管理员全局监控大屏。

---

## 2. 团队成员与模块分工

| 成员 | 角色 | 负责子系统 | 负责数据表 | 负责视图数 | GitHub 账号 |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **顾峰鸣** | 组长 | 系统架构底座、可视化工作流编排与多人实时协同子系统 | `wf_workflow`, `wf_node_edge` | 6 个 | **[待确认]** |
| **张誉诚** | 组员 | AI 调度引擎、SSE 流式运行沙箱与模板生态子系统 | `wf_exec_record`, `wf_exec_node_log` | 6 个 | `killo.linli` **[待确认]** |
| **郝唐鑫** | 组员 | 高校实训教务管理、在线回放评测与学情统计子系统 | `edu_task`, `edu_submission` | 6 个 | **[待确认]** |
| **李俊豪** | 组员 | RBAC 权限治理、Token 算力管控与全局监控大屏子系统 | `sys_user`, `sys_model_audit_log` | 6 个 | `lijunhao` |

---

## 3. 技术栈与环境要求

| 层次 | 技术选型 | 环境版本要求 |
| :--- | :--- | :--- |
| **后端 (`flowmind-backend`)** | Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + Spring Security 6 + JJWT + WebSocket + SSE | **JDK 21**、Maven 3.9+ |
| **前端 (`flowmind-frontend`)** | Vue 3.5 + TypeScript + Vite 5 + Pinia + Vue Router 4 + Element Plus + Vue Flow + Yjs + Monaco Editor + ECharts 5 | **Node.js $\ge 20.x$**、npm 10+ |
| **数据库与缓存** | MySQL 8.0+、Redis 7.x（支持无 Redis 自动回退至内存黑名单缓存） | MySQL 8.0+ |

---

## 4. 快速启动指南

### 4.1 初始化 MySQL 数据库
在 MySQL 8.0 中执行项目根目录下 `sql/init_flowmind.sql`：
```bash
mysql -u root -p < sql/init_flowmind.sql
```
该脚本将自动创建 `flowmind_db` 数据库、8 张数据表及演示初始数据。

### 4.2 启动后端服务 (`flowmind-backend`)
1. 检查 `flowmind-backend/src/main/resources/application.yml` 中的数据库连接密码（默认账号 `root`，密码 `123456`）。
2. 编译并启动服务：
```bash
cd flowmind-backend
mvn clean spring-boot:run
```
- 后端 API 地址：`http://localhost:8080`
- Knife4j 接口文档地址：`http://localhost:8080/doc.html`

### 4.3 启动前端服务 (`flowmind-frontend`)
```bash
cd flowmind-frontend
npm install
npm run dev
```
- 前端访问地址：`http://localhost:5173`

### 4.4 预置演示测试账号（密码统一为 `123456`）

| 角色 | 登录账号 (`username`) | 密码 | 姓名与班级 |
| :--- | :--- | :--- | :--- |
| **系统管理员 (`ADMIN`)** | `admin` | `123456` | 李俊豪(管理员) |
| **指导教师 (`TEACHER`)** | `teacher_wang` | `123456` | 王建国教授 |
| **学生 (`STUDENT` - 组长)** | `gufengming` | `123456` | 顾峰鸣（计算机科学与技术2301班） |
| **学生 (`STUDENT` - 组员)** | `zhangyucheng` | `123456` | 张誉诚（计算机科学与技术2301班） |
| **学生 (`STUDENT` - 组员)** | `haotangxin` | `123456` | 郝唐鑫（计算机科学与技术2301班） |
| **学生 (`STUDENT` - 组员)** | `lijunhao_stu` | `123456` | 李俊豪（计算机科学与技术2301班） |

---

## 5. 开发文档导航 (`docs/`)

在使用 OpenAI Codex 或进行人工开发前，请先阅读以下设计文档：
- [AGENTS.md](./AGENTS.md)：**Codex 最高开发规范与强制约束**
- [docs/PROJECT_OVERVIEW.md](./docs/PROJECT_OVERVIEW.md)：项目总体概述与课程评分映射
- [docs/SRS.md](./docs/SRS.md)：软件需求规格说明书
- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)：系统总体架构与技术机制设计
- [docs/DATABASE.md](./docs/DATABASE.md)：数据库 8 张表结构设计与可执行 SQL
- [docs/API.md](./docs/API.md)：完整 RESTful / SSE / WebSocket 接口文档
- [docs/PROJECT_STRUCTURE.md](./docs/PROJECT_STRUCTURE.md)：完整前后端代码目录树與文件归属
- [docs/BUSINESS_LOGIC.md](./docs/BUSINESS_LOGIC.md)：Kahn 拓扑算法、AI 双模调用与多人协同核心逻辑
- [docs/DEVELOPMENT_TASKS.md](./docs/DEVELOPMENT_TASKS.md)：28 个原子开发任务分解表 (`TASK-001` ~ `TASK-028`)
- [docs/GIT_WORKFLOW.md](./docs/GIT_WORKFLOW.md)：GitHub 多人分支与 PR 协作规范
- [docs/TESTING.md](./docs/TESTING.md)：完整测试计划与核心测试用例
- [docs/CODEX_GUIDE.md](./docs/CODEX_GUIDE.md)：Codex 专属执行指南与一键复制提示词模板
- [FINAL_REVIEW.md](./FINAL_REVIEW.md)：架构一致性审查报告与待确认事项
