# FlowMind 完整 API 接口规范文档 (API)

## 1. 全局通用约定

### 1.1 基础路径与协议
- **后端服务端口**：`8080`
- **RESTful 基础路径**：`/api/v1`
- **WebSocket 协同路径**：`ws://localhost:8080/ws/collab/{workflowId}?token={jwt}`
- **认证方式**：除登录、注册及公开模板列表外，所有接口必须在 HTTP Header 携带：
  ```http
  Authorization: Bearer <JWT_TOKEN>
  Content-Type: application/json
  ```

### 1.2 统一响应体结构 (`Result<T>`)
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1761826800000
}
```

### 1.3 统一分页响应结构 (`PageResult<T>`)
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [],
    "total": 45,
    "pageNum": 1,
    "pageSize": 10
  },
  "timestamp": 1761826800000
}
```

### 1.4 全局标准错误码 (`ErrorCode`)

| 错误码 (`code`) | HTTP 状态码 | 枚举名 | 错误信息 (`message`) | 触发场景 |
| :---: | :---: | :--- | :--- | :--- |
| `200` | 200 | `SUCCESS` | 操作成功 | 正常响应 |
| `40000` | 400 | `PARAM_ERROR` | 请求参数校验失败 | `@Valid` 校验不通过 |
| `40001` | 400 | `USERNAME_EXISTS` | 用户名已被注册 | 注册重名 |
| `40004` | 404 | `RESOURCE_NOT_FOUND` | 请求的资源不存在 | ID 查询为空 |
| `40005` | 400 | `DAG_CYCLE_ERROR` | 工作流存在循环依赖或孤立节点，无法执行 | Kahn 拓扑校验失败 |
| `40006` | 400 | `TASK_NODE_RULE_FAILED`| 提交的工作流未满足课题必含节点要求 | 实训作业校验失败 |
| `40009` | 400 | `SENSITIVE_WORD_BLOCKED`| 请求包含违规敏感词或恶意脚本，已被安全过滤器拦截 | `SensitiveAndXssFilter` 触发 |
| `40100` | 401 | `UNAUTHORIZED` | 未登录或 Token 格式无效 | 未传 Token 或签名错误 |
| `40101` | 401 | `BAD_CREDENTIALS` | 账号或密码错误 | 登录失败 |
| `40102` | 401 | `TOKEN_BLACKLISTED` | 登录凭证已安全退出失效，请重新登录 | 命中 Redis 退出黑名单 |
| `40201` | 402 | `QUOTA_INSUFFICIENT` | Token 算力额度不足，请联系教师或管理员充值 | `token_quota <= 0` |
| `40300` | 403 | `FORBIDDEN` | 权限不足，无法访问该资源 | 角色越权或非协作成员修改画布 |
| `40301` | 403 | `ACCOUNT_DISABLED` | 账号已被管理员封禁 | `sys_user.status == 0` |
| `50000` | 500 | `INTERNAL_ERROR` | 系统内部异常，请稍后重试 | 未预期异常兜底 |

---

## 2. 子系统四接口：认证、RBAC 权限与全局大屏（负责人：李俊豪）

### 2.1 用户注册
- **地址**：`POST /api/v1/auth/register`
- **权限**：公开（`permitAll`）
- **请求参数 (`RegisterReqDTO`)**：
  | 字段 | 类型 | 必填 | 校验规则与说明 |
  | :--- | :--- | :---: | :--- |
  | `username` | String | 是 | `^[a-zA-Z0-9_]{4,32}$` |
  | `password` | String | 是 | 长度 6~32 位 |
  | `realName` | String | 是 | 长度 2~32 位 |
  | `roleType` | String | 是 | 仅允许 `STUDENT` 或 `TEACHER` |
  | `deptClass`| String | 是 | 所属院系班级 |
  | `email` | String | 否 | 合法邮箱格式 |
- **响应示例**：
  ```json
  { "code": 200, "message": "注册成功", "data": { "userId": 7, "username": "stu_001" }, "timestamp": 1761826800000 }
  ```

### 2.2 用户登录
- **地址**：`POST /api/v1/auth/login`
- **权限**：公开（`permitAll`）
- **请求示例**：
  ```json
  { "username": "gufengming", "password": "123456" }
  ```
- **响应示例 (`LoginRespVO`)**：
  ```json
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "tokenId": "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
      "userInfo": {
        "id": 3,
        "username": "gufengming",
        "realName": "顾峰鸣",
        "roleType": "STUDENT",
        "deptClass": "计算机科学与技术2301班",
        "tokenQuota": 46800,
        "tokenUsed": 3200
      }
    },
    "timestamp": 1761826800000
  }
  ```

### 2.3 安全退出
- **地址**：`POST /api/v1/auth/logout`
- **权限**：已登录用户（`STUDENT` / `TEACHER` / `ADMIN`）
- **处理逻辑**：提取当前 Token 的 `jti` 写入 `TokenCacheService.blacklistToken(jti, remainingMillis)`，记录 `LOGOUT` 审计日志。
- **响应示例**：`{ "code": 200, "message": "安全退出成功", "data": true, "timestamp": 1761826800000 }`

### 2.4 获取当前登录用户信息
- **地址**：`GET /api/v1/auth/me`
- **权限**：已登录用户
- **响应示例**：同 `LoginRespVO.userInfo`。

### 2.5 用户多条件分页查询（管理员）
- **地址**：`GET /api/v1/admin/users`
- **权限**：`ADMIN`
- **Query 参数 (`UserQueryDTO`)**：
  - `keyword` (String, 否：匹配 `username` 或 `real_name`)
  - `roleType` (String, 否：`ADMIN`/`TEACHER`/`STUDENT`)
  - `deptClass` (String, 否：模糊匹配班级)
  - `status` (Integer, 否：`1` 或 `0`)
  - `minQuota` / `maxQuota` (Integer, 否：剩余算力区间)
  - `pageNum` (Integer, 默认 1), `pageSize` (Integer, 默认 10)
- **响应示例**：`PageResult<SysUserVO>`。

### 2.6 修改用户角色、状态与算力配额（管理员）
- **地址**：`PUT /api/v1/admin/users/{id}`
- **权限**：`ADMIN`
- **请求示例 (`UserUpdateDTO`)**：
  ```json
  {
    "roleType": "TEACHER",
    "deptClass": "人工智能系",
    "status": 1,
    "tokenQuota": 100000,
    "resetPassword": false
  }
  ```

### 2.7 安全审计日志多条件分页查询（管理员）
- **地址**：`GET /api/v1/admin/audit-logs`
- **权限**：`ADMIN`
- **Query 参数 (`AuditLogQueryDTO`)**：`username`, `moduleName`, `actionType`, `modelProvider`, `httpStatus`, `startTime`, `endTime`, `pageNum`, `pageSize`。
- **响应示例**：`PageResult<SysModelAuditLog>`。

### 2.8 获取/更新大模型通道与敏感词规则配置（管理员）
- **地址**：`GET /api/v1/admin/models/config` 与 `PUT /api/v1/admin/models/config`
- **权限**：`ADMIN`
- **请求/响应载荷 (`ModelConfigDTO`)**：
  ```json
  {
    "activeProvider": "DEEPSEEK",
    "baseUrl": "https://api.deepseek.com/v1",
    "apiKeyMasked": "sk-****89ab",
    "apiKeyPlain": "",
    "modelName": "deepseek-chat",
    "enableMockFallback": true,
    "sensitiveWords": ["drop table", "delete from sys_user", "rm -rf", "违法违禁"]
  }
  ```

### 2.9 全局监控可视化大屏聚合数据（管理员）
- **地址**：`GET /api/v1/admin/dashboard/big-screen`
- **权限**：`ADMIN`（教师亦可只读访问以用于教学演示）
- **响应示例 (`BigScreenStatsVO`)**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "kpi": { "totalUsers": 128, "totalWorkflows": 86, "totalExecutions": 1420, "totalTokensUsed": 684200 },
      "sankeyData": {
        "nodes": [{"name": "计科2301班"}, {"name": "STUDENT"}, {"name": "DEEPSEEK"}],
        "links": [{"source": "计科2301班", "target": "STUDENT", "value": 320000}, {"source": "STUDENT", "target": "DEEPSEEK", "value": 320000}]
      },
      "deptRanking": [{"deptClass": "计算机科学与技术2301班", "workflowCount": 42, "execCount": 680}],
      "hourlyTrend": [{"hour": "08:00", "callCount": 45, "avgLatencyMs": 820}]
    },
    "timestamp": 1761826800000
  }
  ```

---

## 3. 子系统一接口：工作流主从管理与多人协同（负责人：顾峰鸣）

### 3.1 个人工作台概览统计
- **地址**：`GET /api/v1/workflows/dashboard`
- **权限**：已登录用户
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "myWorkflowCount": 5,
      "collabWorkflowCount": 3,
      "pendingTaskCount": 2,
      "remainingTokenQuota": 46800,
      "recentWorkflows": []
    },
    "timestamp": 1761826800000
  }
  ```

### 3.2 工作流多条件分页查询
- **地址**：`GET /api/v1/workflows`
- **权限**：已登录用户
- **Query 参数 (`WorkflowQueryDTO`)**：
  - `keyword` (String, 否：匹配 `wf_name` 或 `description` 或 `tags`)
  - `category` (String, 否：`CODE_ASSIST` / `DATA_ANALYSIS` / `TEXT_GEN` / `KNOWLEDGE_QA` / `OTHER`)
  - `isPublic` (Integer, 否：`0` 或 `1`)
  - `minNodeCount` / `maxNodeCount` (Integer, 否：节点数量区间)
  - `scope` (String, 默认 `ALL`：`MINE` 我创建的 / `COLLAB` 我参与协作的 / `ALL` 全部可见)
  - `startTime` / `endTime` (String, 否)
  - `pageNum` (Integer, 默认 1), `pageSize` (Integer, 默认 10)
- **响应示例**：`PageResult<WorkflowSummaryVO>`。

### 3.3 创建工作流（自动初始化主从表默认首尾节点）
- **地址**：`POST /api/v1/workflows`
- **权限**：已登录用户
- **请求示例 (`WorkflowCreateDTO`)**：
  ```json
  {
    "wfName": "Python代码单元测试自动生成流",
    "description": "输入Python函数自动生成Pytest测试用例并检查边界条件",
    "category": "CODE_ASSIST",
    "tags": "Python,单元测试,Pytest",
    "isPublic": 0
  }
  ```
- **响应示例**：返回新建的 `WorkflowDetailVO`（含主表信息及默认生成的 `node_input_1` 和 `node_output_1` 从表记录）。

### 3.4 获取工作流主从完整详情（含全部节点与连线）
- **地址**：`GET /api/v1/workflows/{id}`
- **权限**：已登录用户（创建人、协作成员、公开工作流或批改教师）
- **响应示例 (`WorkflowDetailVO`)**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "workflow": {
        "id": 1,
        "wfCode": "WF-20261030-001",
        "wfName": "Java代码安全缺陷智能审计与修复工作流",
        "category": "CODE_ASSIST",
        "tags": "Java,代码评审,安全审计,RAG",
        "creatorId": 3,
        "creatorName": "顾峰鸣",
        "collaboratorIds": [3, 4, 5, 6],
        "isPublic": 1,
        "nodeCount": 4,
        "edgeCount": 3,
        "versionTag": "v1.2.0"
      },
      "nodes": [
        {
          "id": 101,
          "elementKey": "node_input_1",
          "elementType": "NODE",
          "nodeType": "INPUT",
          "labelName": "Java源码输入",
          "positionJson": { "x": 80, "y": 200 },
          "configJson": { "variables": [{ "name": "codeSnippet", "type": "string", "required": true }] }
        }
      ],
      "edges": [
        {
          "id": 105,
          "elementKey": "edge_1_2",
          "elementType": "EDGE",
          "labelName": "源码传递",
          "sourceKey": "node_input_1",
          "targetKey": "node_know_1",
          "sourceHandle": "default"
        }
      ]
    },
    "timestamp": 1761826800000
  }
  ```

### 3.5 保存画布节点与连线（主从表级联事务保存）
- **地址**：`PUT /api/v1/workflows/{id}/canvas`
- **权限**：工作流创建人或 `collaborator_ids` 中的协作成员
- **请求示例 (`CanvasSaveDTO`)**：
  ```json
  {
    "wfName": "Java代码安全缺陷智能审计与修复工作流",
    "description": "更新后的描述",
    "isPublic": 1,
    "createSnapshot": true,
    "newVersionTag": "v1.3.0",
    "nodes": [
      {
        "elementKey": "node_input_1",
        "nodeType": "INPUT",
        "labelName": "Java源码输入",
        "positionJson": { "x": 100, "y": 200 },
        "configJson": { "variables": [{ "name": "codeSnippet", "type": "string" }] }
      }
    ],
    "edges": [
      {
        "elementKey": "edge_1_2",
        "labelName": "传递",
        "sourceKey": "node_input_1",
        "targetKey": "node_llm_1",
        "sourceHandle": "default"
      }
    ]
  }
  ```

### 3.6 版本快照列表与一键回滚
- **地址**：
  - 获取版本列表：`GET /api/v1/workflows/{id}/versions`
  - 回滚到指定版本：`POST /api/v1/workflows/{id}/versions/rollback`（Body: `{"versionTag": "v1.1.0"}`）
- **权限**：创建人或协作成员

### 3.7 协作成员管理与删除工作流
- **更新协作成员**：`PUT /api/v1/workflows/{id}/collaborators`（Body: `{"collaboratorIds": [3, 4, 5, 6]}`）
- **删除工作流（级联删除从表）**：`DELETE /api/v1/workflows/{id}`

---

## 4. 子系统二接口：AI 执行引擎、流式沙箱与模板广场（负责人：张誉诚）

### 4.1 触发工作流 SSE 流式运行
- **地址**：`POST /api/v1/engine/execute/stream`
- **权限**：已登录用户
- **响应 Content-Type**：`text/event-stream;charset=UTF-8`
- **请求示例 (`WorkflowExecuteReqDTO`)**：
  ```json
  {
    "workflowId": 1,
    "triggerMode": "SANDBOX",
    "inputPayload": {
      "codeSnippet": "stmt.execute(\"SELECT * FROM sys_user WHERE username = '\" + name + \"'\");"
    },
    "canvasOverride": null
  }
  ```
  > 注：当教师在 `TeacherCanvasReviewView.vue` 回放学生快照时，可传入 `canvasOverride` 直接运行快照拓扑。

### 4.2 同步运行工作流（供自动化测试与非流式调用）
- **地址**：`POST /api/v1/engine/execute/sync`
- **权限**：已登录用户
- **请求体**：同 `WorkflowExecuteReqDTO`
- **响应示例**：返回完整的 `ExecRecordDetailVO`（包含主表 `wf_exec_record` 与从表 `wf_exec_node_log` 数组）。

### 4.3 自然语言一句话生成工作流 (Text-to-Flow)
- **地址**：`POST /api/v1/engine/text-to-flow`
- **权限**：已登录用户
- **请求示例 (`TextToFlowReqDTO`)**：
  ```json
  {
    "prompt": "帮我搭建一个先对用户输入进行条件判断，如果是Java问题则检索阿里规约知识库并调用大模型解答的工作流",
    "category": "CODE_ASSIST"
  }
  ```
- **响应示例**：返回新创建的 `WorkflowDetailVO`（可直接跳转 `/workflow/editor/{id}`）。

### 4.4 运行记录主表多条件分页查询与从表明细查询
- **主表多条件分页**：`GET /api/v1/engine/records`
  - Query 参数：`workflowId`, `execStatus`, `triggerMode`, `minDuration`, `maxDuration`, `startTime`, `endTime`, `pageNum`, `pageSize`
- **单次运行主从详情（含各节点日志从表）**：`GET /api/v1/engine/records/{execRecordId}`
  - 响应示例 (`ExecRecordDetailVO`)：
    ```json
    {
      "code": 200,
      "message": "操作成功",
      "data": {
        "record": {
          "id": 1,
          "execNo": "EXEC-20261030-0001",
          "workflowId": 1,
          "wfName": "Java代码安全缺陷智能审计与修复工作流",
          "execStatus": "SUCCESS",
          "totalTokens": 486,
          "durationMs": 1420,
          "inputPayload": { "codeSnippet": "..." },
          "finalOutput": { "report": "..." }
        },
        "nodeLogs": [
          {
            "id": 1,
            "stepOrder": 1,
            "nodeKey": "node_input_1",
            "nodeName": "Java源码输入",
            "nodeType": "INPUT",
            "stepStatus": "SUCCESS",
            "tokenCost": 0,
            "costMs": 12,
            "nodeInput": "...",
            "nodeOutput": "..."
          }
        ]
      },
      "timestamp": 1761826800000
    }
    ```

### 4.5 模板广场多条件查询、点赞与一键 Fork 克隆
- **模板多条件查询**：`GET /api/v1/market/templates`
  - Query 参数：`keyword`, `category`, `tag`, `minNodeCount`, `maxNodeCount`, `sortBy` (`popular` 按 `fork_count` / `likes` 按 `like_count` / `latest` 按更新时间), `pageNum`, `pageSize`
- **模板详情只读预览**：`GET /api/v1/market/templates/{id}`
- **点赞模板**：`POST /api/v1/market/templates/{id}/like`
- **一键 Fork 克隆模板到个人空间**：`POST /api/v1/market/templates/{id}/fork`
  - 响应：`{ "code": 200, "message": "成功克隆至个人工作流空间", "data": { "newWorkflowId": 12 } }`

---

## 5. 子系统三接口：高校实训教务与评测统计（负责人：郝唐鑫）

### 5.1 实训课题多条件分页查询（师生共用）
- **地址**：`GET /api/v1/edu/tasks`
- **权限**：已登录用户
- **Query 参数 (`EduTaskQueryDTO`)**：`keyword`, `courseName`, `targetClass`, `taskStatus`, `pageNum`, `pageSize`。

### 5.2 发布/修改/删除实训课题（教师主表 CRUD）
- **新建课题**：`POST /api/v1/edu/tasks`（权限：`TEACHER` / `ADMIN`）
- **修改课题**：`PUT /api/v1/edu/tasks/{id}`（权限：`TEACHER` / `ADMIN`）
- **删除课题（级联清理从表）**：`DELETE /api/v1/edu/tasks/{id}`（权限：`TEACHER` / `ADMIN`）
- **请求示例 (`EduTaskSaveDTO`)**：
  ```json
  {
    "taskTitle": "实训二：多分支条件路由客服智能体构建",
    "courseName": "Web应用综合实训",
    "targetClass": "计算机科学与技术2301班",
    "taskRequirement": "设计包含条件分支节点的智能体工作流...",
    "requiredNodesJson": { "minNodes": 4, "mustInclude": ["INPUT", "CONDITION", "LLM", "OUTPUT"] },
    "rubricJson": [
      { "item": "拓扑完整性与节点规范", "maxScore": 30 },
      { "item": "Prompt工程与知识库结合度", "maxScore": 40 },
      { "item": "沙箱运行效果与实验报告", "maxScore": 30 }
    ],
    "deadline": "2026-11-15 23:59:59",
    "taskStatus": 1
  }
  ```

### 5.3 学生提交/更新实训作业（从表写入与快照固化）
- **地址**：`POST /api/v1/edu/submissions`
- **权限**：`STUDENT`
- **请求示例 (`SubmissionSubmitDTO`)**：
  ```json
  {
    "taskId": 1,
    "workflowId": 1,
    "studentReport": "本次实训我们四人小组协同设计了包含阿里规约检索与DeepSeek代码审计的工作流..."
  }
  ```
- **响应示例**：返回 `EduSubmissionVO`（包含自动计算的 `isLate` 与 `autoCheckPassed` 结果）。

### 5.4 实训作业多条件分页查询与详情获取
- **地址**：`GET /api/v1/edu/submissions`
- **权限**：`TEACHER` / `ADMIN`（查全班）；`STUDENT`（仅查本人提交记录）
- **Query 参数 (`SubmissionQueryDTO`)**：
  - `taskId` (Long, 否)
  - `targetClass` (String, 否)
  - `reviewStatus` (Integer, 否：`0` 待批改 / `1` 已批改 / `2` 打回重做)
  - `isLate` (Integer, 否：`0` 或 `1`)
  - `autoCheckPassed` (Integer, 否：`0` 或 `1`)
  - `minScore` / `maxScore` (BigDecimal, 否：分数段区间)
  - `studentKeyword` (String, 否：匹配学生学号或真实姓名)
  - `pageNum`, `pageSize`
- **获取单份作业完整详情（含 `canvasSnapshotJson`）**：`GET /api/v1/edu/submissions/{id}`

### 5.5 教师批改评分与写评语
- **地址**：`PUT /api/v1/edu/submissions/{id}/grade`
- **权限**：`TEACHER` / `ADMIN`
- **请求示例 (`SubmissionGradeDTO`)**：
  ```json
  {
    "rubricDetailJson": {
      "拓扑完整性与节点规范": 28,
      "Prompt工程与知识库结合度": 38,
      "沙箱运行效果与实验报告": 29
    },
    "score": 95.0,
    "teacherComment": "画布节点组织严密，沙箱实测输出准确，予以优秀评定！",
    "reviewStatus": 1
  }
  ```

### 5.6 班级学情 ECharts 统计分析报表
- **地址**：`GET /api/v1/edu/analytics`
- **权限**：`TEACHER` / `ADMIN`
- **Query 参数**：`taskId` (可选), `targetClass` (可选)
- **响应示例 (`EduAnalyticsVO`)**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "summary": { "totalSubmissions": 38, "gradedCount": 35, "passRate": 94.7, "avgScore": 86.4 },
      "scoreBuckets": [
        { "bucket": "<60 (不及格)", "count": 2 },
        { "bucket": "60-69 (合格)", "count": 4 },
        { "bucket": "70-79 (中等)", "count": 8 },
        { "bucket": "80-89 (良好)", "count": 13 },
        { "bucket": "90-100 (优秀)", "count": 11 }
      ],
      "radarDimensions": [
        { "dimension": "拓扑设计规范度", "avgRate": 91.5 },
        { "dimension": "大模型Prompt质量", "avgRate": 85.0 },
        { "dimension": "分支逻辑完备性", "avgRate": 82.3 },
        { "dimension": "沙箱执行成功率", "avgRate": 93.0 },
        { "dimension": "实训报告质量", "avgRate": 88.2 }
      ],
      "dailySubmissionTrend": [
        { "date": "2026-10-28", "submitCount": 12, "debugCount": 65 }
      ],
      "errorNodeDistribution": [
        { "nodeType": "LLM", "errorCount": 14 },
        { "nodeType": "CODE", "errorCount": 9 },
        { "nodeType": "CONDITION", "errorCount": 5 }
      ]
    },
    "timestamp": 1761826800000
  }
  ```
