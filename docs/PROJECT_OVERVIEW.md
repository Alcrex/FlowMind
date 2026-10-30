# FlowMind（智流）项目总体概述 (PROJECT_OVERVIEW)

## 1. 基本信息

| 属性 | 内容 |
| :--- | :--- |
| **项目中文全称** | 智流（FlowMind）——支持多人实时协同的 AI 工作流可视化编排与实训平台的设计与实现 |
| **项目英文全称** | FlowMind: A Real-Time Collaborative Visual AI Workflow Orchestration and Training Platform |
| **项目代号 / 仓库包名** | `flowmind` (`com.flowmind.platform`) |
| **开发周期** | 3 周（第 1 周：需求与概要设计；第 2 周：详细设计与编码实现；第 3 周：系统测试、文档与答辩） |
| **团队规模与成员** | 4 人（组长：**顾峰鸣**；组员：**张誉诚**、**郝唐鑫**、**李俊豪**） |
| **已接入 GitHub 成员账号** | `lijunhao`（李俊豪）、`killo.linli`（**[待确认]** 对应张誉诚或郝唐鑫，其余两位成员 GitHub 账号 **[待确认]**） |
| **主要编程智能体** | OpenAI Codex（严格依据根目录 `AGENTS.md` 与 `docs/` 全套开发文档执行开发） |

---

## 2. 项目背景与立项依据

随着大语言模型（LLM）与智能体（AI Agent）技术的普及，高校计算机与软件工程专业实训正在从“单一代码编写”向“复合型 AI 工作流编排（Compound AI Systems）”演进。然而，现有教学与实训环境存在以下核心痛点：

1. **缺乏多人同屏实时协同能力**：传统 Web 实训平台仅支持单人独立操作表单，小组成员无法像使用 Figma、Coze（扣子）或 Dify 一样，在同一个画布上实时看到队友的彩色鼠标光标并同步拖拽编排节点。
2. **通用 AI 编排工具与高校教务管理脱节**：商业 AI 平台缺乏面向高校课程实训的“教师发布课题与必含节点规则 $\rightarrow$ 学生小组协同编排 $\rightarrow$ 提交工作流画布 JSON 快照与实训报告 $\rightarrow$ 教师在线沙箱回放试跑并分项打分 $\rightarrow$ 班级学情统计分析”完整教务闭环。
3. **算力与安全缺乏校级治理**：实训过程中大模型 Token 消耗缺乏配额限制，且缺乏针对违规提示词（Prompt）的敏感词过滤与操作审计。

为此，本项目设计并实现 **FlowMind（智流）** 平台，将**可视化节点拖拽编排、WebSocket + Yjs 多人实时同屏协同、DAG 流式执行沙箱、高校实训作业评测、RBAC 权限与算力监控大屏**融为一体。

---

## 3. 核心目标与课程硬性评分达标矩阵

本项目严格对标《26-27-1 Web应用综合实训要求》中的最高档（优秀 90~100 分）标准，所有硬性指标设计如下：

| 课程考核硬性要求 | FlowMind 实际设计指标 | 负责模块 / 实现方式 |
| :--- | :--- | :--- |
| **前后端主流分离框架** | **Spring Boot 3.3 (JDK 21) + Vue 3.5 (TypeScript)** | 全栈前后端分离，RESTful API + SSE + WebSocket |
| **视图文件数 $\ge 20$ 个** | **24 个独立 `.vue` 视图文件**（每人严格负责 6 个） | 分布在 `auth`、`error`、`workspace`、`workflow`、`engine`、`market`、`edu`、`admin` 8 个视图目录 |
| **数据库表 $\ge 6$ 张，含 $\ge 2$ 对主从表** | **8 张核心业务表，含 3 对严格外键关联的主从表** | 主从对1：`wf_workflow` $\leftrightarrow$ `wf_node_edge`<br>主从对2：`wf_exec_record` $\leftrightarrow$ `wf_exec_node_log`<br>主从对3：`edu_task` $\leftrightarrow$ `edu_submission`<br>支撑表：`sys_user`、`sys_model_audit_log` |
| **增删改查与多条件组合查询** | 全表支持标准 CRUD；**4 大子系统均包含 $\ge 5$ 个维度的多条件动态组合查询** | 基于 MyBatis-Plus `LambdaQueryWrapper` 实现关键词、分类、区间、状态、时间范围组合查询与分页 |
| **至少 2 种用户角色与权限控制** | **3 类角色**（`STUDENT` 学生、`TEACHER` 教师、`ADMIN` 管理员） | Spring Security + JWT + `@PreAuthorize` 角色鉴权 + Vue Router 前置守卫 |
| **密码编码器（Encoder）** | **`BCryptPasswordEncoder`** 强哈希加盐加密 | 注册、改密、管理员重置密码全流程强制加密 |
| **过滤器（Filter）** | **3 层自定义过滤器链** | `CorsFilter`（跨域）、`JwtAuthenticationFilter`（认证与黑名单校验）、`SensitiveAndXssFilter`（XSS 与 AI 敏感词拦截） |
| **全局异常处理（Exception）** | **`@RestControllerAdvice` 统一异常拦截** | 拦截 `BusinessException`、参数校验异常、权限异常、AI 超时异常并记录审计表 |
| **安全退出（Secure Logout）** | **JWT 令牌吊销 + Redis 黑名单机制** | 退出时将 Token `jti` 写入 Redis 黑名单直至过期，前端清空 Pinia 与 LocalStorage |
| **统计分析（Statistics）** | **教师端学情分析 + 管理员端全局监控大屏** | 基于 Apache ECharts 5 实现直方图、五维雷达图、折线图、饼图、桑基图、热力图 |

---

## 4. 目标用户与核心功能范围

### 4.1 学生 / 创作者 (`STUDENT`)
- **个人工作台與工作流管理**：查看待办实训任务、最近协同画布；对工作流进行多条件查询、创建、克隆、版本快照回滚。
- **可视化节点拖拽与多人实时协同**：在 Vue Flow 画布中拖拽 6 类标准节点（`INPUT`、`LLM`、`CODE`、`KNOWLEDGE`、`CONDITION`、`OUTPUT`），配置 Monaco Editor 脚本与提示词；与同组同学实时同步鼠标彩色光标与节点位置变更。
- **沙箱流式调试与一句话生成（Text-to-Flow）**：输入自然语言自动生成工作流拓扑；一键触发 DAG 引擎运行，通过 SSE 实时接收各节点流光高亮状态与大模型打字机流式输出。
- **模板广场与实训作业提交**：多条件检索公开模板并一键 Fork；将工作流画布快照绑定至教师发布的实训课题并提交实验报告。

### 4.2 指导教师 (`TEACHER`)
- **实训课题发布与规则配置**：发布面向指定班级的实训课题，设置截止时间、必含节点类型校验规则与分项评分标准（Rubric）。
- **作业多条件筛选与在线沙箱评测**：多条件筛选本班学生提交的作业，一键在浏览器端加载学生提交时刻的画布快照并在线试跑验证，录入分项得分与评语。
- **班级学情可视化统计**：查看班级作业提交率、成绩分布直方图、学生工程能力五维雷达图、高频报错节点类型占比饼图。

### 4.3 系统管理员 (`ADMIN`)
- **师生用户与 RBAC 权限管理**：多条件查询用户、新增/禁用账号、分配角色、调整或充值师生 Token 算力额度。
- **大模型通道与敏感词治理**：配置 DeepSeek / 通义千问 API Key 与本地降级策略，维护敏感词过滤规则并审计全部系统操作与异常日志。
- **全校 AI 实训监控大屏**：实时可视化展示全站并发情况、各院系 Token 算力消耗桑基图、24 小时调用热力图。

---

## 5. 技术栈与运行环境要求

### 5.1 后端技术栈 (`flowmind-backend`)
- **语言与运行时**：Java 21 (开启虚拟线程 `spring.threads.virtual.enabled=true`)
- **核心框架**：Spring Boot 3.3.5 (`spring-boot-starter-web`, `spring-boot-starter-websocket`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-aop`)
- **持久层**：MyBatis-Plus 3.5.7 + MySQL Connector/J 8.3.0 + HikariCP
- **缓存与会话**：Spring Data Redis (`StringRedisTemplate`) + Lettuce（支持本地内嵌/降级内存缓存以便无 Redis 环境快速单机测试，生产/联调使用 Redis 7.x）
- **认证与工具**：`jjwt` (0.12.5)、`hutool-all` (5.8.28)、`lombok`、`springdoc-openapi-starter-webmvc-ui` (Knife4j 4.5.0)
- **AI 与工作流引擎**：确定性 DAG 拓扑执行引擎（基于 Kahn 算法 + 策略模式节点执行器）+ 标准 OpenAI/DeepSeek/Qwen 兼容 HTTP/SSE 客户端（支持真实 API Key 调用及无 Key 时的智能流式沙箱降级模式，确保评测 100% 可运行）。

### 5.2 前端技术栈 (`flowmind-frontend`)
- **核心底座**：Node.js $\ge 20.x$、Vue 3.5 + TypeScript 5.5 + Vite 5.4
- **路由与状态**：Vue Router 4.4 + Pinia 2.2
- **UI 与样式**：Element Plus 2.8 + `@element-plus/icons-vue` + TailwindCSS 3.4
- **核心交互组件**：
  - `@vue-flow/core` + `@vue-flow/background` + `@vue-flow/controls` + `@vue-flow/minimap`（可视化工作流节点画布）
  - `yjs` + 原生 WebSocket 协同状态适配器（多人实时光标与画布同步）
  - `monaco-editor`（代码与提示词高亮编辑器）
  - `echarts` 5.5（统计分析图表与监控大屏）
  - `axios` + `@microsoft/fetch-event-source`（REST 请求拦截与 SSE 流式接收）

---

## 6. 最终交付物清单

根据实训要求，第 3 周结束时需完整交付：
1. **源代码工程**：包含 `flowmind-backend`、`flowmind-frontend` 及 `sql/init_flowmind.sql`，满足编译零错误、测试全通过。
2. **数据库与测试数据**：8 张数据表完整建表脚本及覆盖 3 类角色、4 大子系统的开箱即用演示数据。
3. **实训报告与部署说明**：遵循《26-27-1Web应用综合实训要求.docx》命名规范的完整实训报告（打印版+电子档）及 `README.md` 部署文档。
4. **4 人分模块答辩演示系统**：支持现场演示双窗口实时光标协同、SSE 流式工作流运行、教师沙箱评测及管理员监控大屏。
