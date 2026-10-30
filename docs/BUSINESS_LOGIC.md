# FlowMind 核心业务逻辑与算法设计文档 (BUSINESS_LOGIC)

## 1. 工作流有向无环图 (DAG) 拓扑排序与条件分支执行算法（张誉诚负责）

### 1.1 算法目标
给定工作流画布中的节点集合 $V$（`element_type = 'NODE'`）和有向连线集合 $E$（`element_type = 'EDGE'`），验证图结构的合法性（无环路、必须包含至少一个 `INPUT` 和一个 `OUTPUT` 节点），并按拓扑序依次执行节点。当遇到 `CONDITION` 节点时，根据布尔表达式求值结果仅激活对应分支（`source_handle = 'true'` 或 `'false'`），对其余分支节点标记为 `SKIPPED`。

### 1.2 核心伪代码与实现蓝图 (`DagTopologicalSorter.java` & `WorkflowDagExecutor.java`)

```java
public class DagTopologicalSorter {

    /**
     * 使用 Kahn 算法对画布节点进行拓扑排序并检测环路
     * @param nodes 节点列表
     * @param edges 连线列表
     * @return 按拓扑序排列的节点列表
     * @throws BusinessException 若存在环路或缺少首尾节点则抛出 ErrorCode.DAG_CYCLE_ERROR (40005)
     */
    public List<WfNodeEdge> sortAndValidate(List<WfNodeEdge> nodes, List<WfNodeEdge> edges) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(ErrorCode.DAG_CYCLE_ERROR, "画布节点不能为空");
        }
        boolean hasInput = nodes.stream().anyMatch(n -> "INPUT".equals(n.getNodeType()));
        boolean hasOutput = nodes.stream().anyMatch(n -> "OUTPUT".equals(n.getNodeType()));
        if (!hasInput || !hasOutput) {
            throw new BusinessException(ErrorCode.DAG_CYCLE_ERROR, "工作流必须至少包含一个输入节点(INPUT)和一个输出节点(OUTPUT)");
        }

        Map<String, WfNodeEdge> nodeMap = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (WfNodeEdge node : nodes) {
            nodeMap.put(node.getElementKey(), node);
            inDegree.put(node.getElementKey(), 0);
            adjList.put(node.getElementKey(), new ArrayList<>());
        }

        for (WfNodeEdge edge : edges) {
            String u = edge.getSourceKey();
            String v = edge.getTargetKey();
            if (nodeMap.containsKey(u) && nodeMap.containsKey(v)) {
                adjList.get(u).add(v);
                inDegree.put(v, inDegree.get(v) + 1);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<WfNodeEdge> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            sorted.add(nodeMap.get(curr));
            for (String next : adjList.get(curr)) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) {
                    queue.offer(next);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new BusinessException(ErrorCode.DAG_CYCLE_ERROR, "工作流画布存在循环依赖环路，请检查节点连线");
        }
        return sorted;
    }
}
```

### 1.3 上下文变量插值替换规则 (`TemplateInterpolator`)
画布中的后续节点可通过双花括号语法引用前序节点的输出：
- `{{node_input_1.codeSnippet}}` 或 `{{input.codeSnippet}}`：引用初始输入载荷中的 `codeSnippet` 字段。
- `{{node_know_1.output}}`：引用 `element_key = 'node_know_1'` 节点的执行输出字符串。
- **正则替换实现**：使用 `Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}")` 从运行时上下文 `Map<String, Object> runtimeContext` 中取值替换；若变量不存在则替换为空字符串 `""`，绝不抛出 `NullPointerException`。

### 1.4 6 类标准节点的具体执行逻辑

| 节点类型 (`node_type`) | `config_json` 关键字段 | 执行逻辑说明 | Token 消耗计算 |
| :--- | :--- | :--- | :--- |
| `INPUT` | `variables: [{name, type, defaultValue}]` | 校验并合并用户传入的 `inputPayload` 与默认值，写入 `runtimeContext` | `0` |
| `KNOWLEDGE` | `knowledgeBase`, `queryTemplate`, `topK` | 对 `queryTemplate` 变量插值后，在内置专业知识库（含《阿里巴巴Java开发手册》《OWASP Web安全规范》《Spring Boot最佳实践》《Vue3工程规范》词条库）中进行 BM25/关键词相关度检索，返回 TopK 条规范文本 | 固定 `50 ~ 100` Tokens（向量/检索折算） |
| `CODE` | `scriptLang`, `ruleType`, `expression` | 支持对输入文本执行安全的字符串清洗、正则提取、长度统计或 JSON 格式化处理 | `0` |
| `CONDITION` | `fieldExpr`, `operator` (`CONTAINS`/`EQUALS`/`LENGTH_GT`), `targetValue` | 计算布尔结果 `true` 或 `false`，仅激活 `source_handle` 匹配该布尔值的下游连线，其余下游分支标记为 `SKIPPED` | `0` |
| `LLM` | `model`, `temperature`, `systemPrompt`, `userPromptTemplate` | 将 `userPromptTemplate` 插值后调用 `LlmClientAdapter`，支持真实 DeepSeek/Qwen 流式 API 与本地智能流式沙箱双模输出 | 按 `(prompt.length + output.length) / 2` 估算或读取 API 返回的 `usage.total_tokens` |
| `OUTPUT` | `outputFormat`, `resultTemplate` | 将 `resultTemplate` 插值渲染为最终 Markdown 或 JSON 结果，写入 `wf_exec_record.final_output` | `0` |

---

## 2. AI 大模型调用与智能流式降级策略 (`LlmClientAdapter.java`)

### 2.1 设计背景
实训与自动化测试环境中，未必每台机器都配置了外网付费 API Key。因此 `LlmClientAdapter` 必须支持**“真实大模型 API + 确定性智能流式沙箱降级”双模无缝切换**：

1. **真实 API 模式**：当管理员在 `ModelAndAuditLogView.vue`（或 `application.yml`）中配置了有效的 `apiKey` 且 `activeProvider` 为 `DEEPSEEK` 或 `QWEN` 时，使用 Java 21 `HttpClient` 向 `https://api.deepseek.com/v1/chat/completions`（或通义千问兼容端点）发送 `stream: true` 请求，实时解析 `data: {"choices":[{"delta":{"content":"..."}}]}` 并回调 `Consumer<String> onTokenChunk`。
2. **本地智能流式沙箱模式 (`MOCK_LOCAL` 或网络异常自动降级)**：
   - 当未配置 API Key、处于单元测试环境或外网连接超时时，自动启用内置智能响应引擎。
   - 引擎根据 `systemPrompt` 和 `userPrompt` 的语义特征（如包含 `SQL`、`代码`、`漏洞`、`周报`、`测试` 等关键词），动态生成结构化的专业 Markdown 分析报告，并按每块 6~10 个字符、间隔 25ms（测试模式下间隔 1ms）流式回调 `onTokenChunk`，使前端 SSE 打字机效果与真实大模型完全一致。

### 2.2 一句话生成工作流 (Text-to-Flow) 合成算法
当用户在 `TextToFlowView.vue` 输入自然语言需求 `prompt` 时：
1. 分析 `prompt` 中的意图关键词：
   - 若包含“知识库 / 规约 / 检索 / RAG”，自动加入 `KNOWLEDGE` 节点；
   - 若包含“判断 / 分支 / 如果 / 条件”，自动加入 `CONDITION` 节点及双路 `LLM` 分支；
   - 若包含“脚本 / 清洗 / 提取 / 转换”，自动加入 `CODE` 节点；
2. 自动计算每个节点的水平流式画布坐标 `position_json`（起点 `x=80, y=220`，每步递增 $\Delta x = 280$，分支上下偏移 $\Delta y = \pm 120$）。
3. 事务写入主表 `wf_workflow` 和从表 `wf_node_edge`，直接返回新生成的完整 `WorkflowDetailVO`。

---

## 3. 多人实时同屏协同状态同步逻辑（顾峰鸣负责）

### 3.1 WebSocket 握手与鉴权
客户端连接 `ws://localhost:8080/ws/collab/{workflowId}?token={jwt}` 时：
1. 从 Query String 提取 `token`，调用 `JwtUtil.parseToken(token)` 校验合法性并检查 `TokenCacheService.isBlacklisted(jti)`。
2. 将解析出的 `userId`、`realName` 存入 `session.getAttributes()`。
3. 将 `session` 加入房间 `roomSessions.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>()).put(userId, session)`。
4. 向房间内所有客户端广播 `PRESENCE_SYNC` 消息，包含当前在线的所有协作者列表 `[{userId, realName, color}]`。

### 3.2 协同消息帧格式与广播逻辑 (`CollabMessage.java`)
```json
{
  "type": "CURSOR_MOVE | GRAPH_DELTA | YJS_UPDATE | PRESENCE_SYNC",
  "workflowId": 1,
  "senderId": 3,
  "senderName": "顾峰鸣",
  "senderColor": "#3B82F6",
  "payload": {
    "x": 420.5,
    "y": 185.0,
    "selectedNodeKey": "node_llm_1",
    "nodes": [],
    "edges": []
  },
  "timestamp": 1761826800000
}
```
- **防回声机制**：服务端收到客户端 A 发送的 `CURSOR_MOVE` 或 `GRAPH_DELTA` 后，仅转发给同一 `workflowId` 房间内 **`userId != senderId`** 的其他在线客户端，避免发送端自身画布抖动。

---

## 4. 实训作业快照隔离与必含节点自动预检逻辑（郝唐鑫负责）

### 4.1 为什么必须使用画布快照 (`canvas_snapshot_json`)
学生在提交实训作业后，可能会继续在个人画布中修改或删除节点。因此，在调用 `POST /api/v1/edu/submissions` 时：
1. 后端实时查询当前 `workflow_id` 对应的全部 `wf_node_edge` 记录。
2. 将 `{ workflow, nodes, edges }` 完整序列化为 JSON 字符串存入 `edu_submission.canvas_snapshot_json`。
3. 教师在 `TeacherCanvasReviewView.vue` 批改及沙箱试跑时，读取的是固化在 `canvas_snapshot_json` 中的快照数据，确保批改公平性与可追溯性。

### 4.2 必含节点规则自动预检算法
读取 `edu_task.required_nodes_json`（例如 `{"minNodes": 4, "mustInclude": ["INPUT", "KNOWLEDGE", "LLM", "OUTPUT"]}`）：
1. 统计快照中 `element_type == 'NODE'` 的节点总数 `actualNodeCount` 及节点类型集合 `actualNodeTypes`。
2. 若 `actualNodeCount >= minNodes` 且 `actualNodeTypes.containsAll(mustInclude)`，则设置 `auto_check_passed = 1`；否则设置 `auto_check_passed = 0`（允许提交但向学生和批改教师高亮展示未达标预警标签）。

---

## 5. 安全过滤器、密码编码器与安全退出逻辑（李俊豪负责）

1. **BCrypt 密码编码器**：
   - 在 `SecurityConfig` 中注册 `@Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }`。
   - 注册与重置密码时调用 `passwordEncoder.encode(rawPassword)`；登录校验时调用 `passwordEncoder.matches(rawPassword, user.getPasswordHash())`。
2. **安全退出（Redis + 内存双模黑名单）**：
   - `JwtUtil` 生成 Token 时写入唯一 UUID 作为 `jti` Claim。
   - 用户调用 `POST /api/v1/auth/logout` 时，计算 Token 剩余存活毫秒数 `ttlMillis = exp.getTime() - System.currentTimeMillis()`。
   - 调用 `tokenCacheService.blacklistToken(jti, ttlMillis)`。
   - `JwtAuthenticationFilter` 每次拦截请求时调用 `tokenCacheService.isBlacklisted(jti)`，若返回 `true` 则立即拒绝访问并返回错误码 `40102`。
