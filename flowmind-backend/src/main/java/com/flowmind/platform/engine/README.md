# TASK-015～020：AI 执行引擎与模板生态

本模块由张誉诚负责。变更限定在后端 `engine` 包、本模块测试，以及前端 `views/engine`、`views/market`、`engineAndMarketApi.ts`、`sseClient.ts`，复用现有工作流实体和事务服务。

## 运行与模型配置

默认无需 API Key：自动使用本地规则生成与输入相关的流式 Markdown 报告。知识检索使用内置阿里 Java 规约、OWASP、Spring Boot、Vue 3 条目和关键词相关度排序。CODE 只执行安全文本操作，不运行用户提供的系统脚本。正则提取限制输入长度、分组和量词，超出安全子集返回节点错误。

真实模型使用 Java 21 HttpClient 解析兼容 OpenAI 协议的 SSE。管理员保存配置后，下一次调用读取 `SysAuditAndConfigService.configuredRuntimeConfig()` 提供的解密配置快照；通道、URL、Key、模型名称与降级开关作为整体应用，优先于节点与环境默认值。描述性种子审计不算已保存配置。尚未保存管理员配置时，支持以下 Spring Boot 属性（可使用对应的大写下划线环境变量，或启动参数）：

| 属性 | 默认值 / 说明 |
| --- | --- |
| `flowmind.llm.active-provider` | `DEEPSEEK`；节点未指定模型时使用 |
| `flowmind.llm.deepseek.api-key` | 空；亦读取 `DEEPSEEK_API_KEY` |
| `flowmind.llm.deepseek.base-url` | `https://api.deepseek.com/v1` |
| `flowmind.llm.deepseek.model-name` | `deepseek-chat` |
| `flowmind.llm.qwen.api-key` | 空；亦读取 `QWEN_API_KEY` |
| `flowmind.llm.qwen.base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `flowmind.llm.qwen.model-name` | `qwen-plus` |
| `flowmind.llm.enable-mock-fallback` | `true`；远端失败且尚未输出时降级 |
| `flowmind.llm.timeout-seconds` | `60`；连接超时另设为 5 秒 |
| `flowmind.llm.mock-chunk-delay-ms` | 普通环境 `25`，test profile `1` |

远端流已经输出部分内容后发生失败时，节点记为 ERROR，不重复拼接一份本地报告。测试使用本地 HTTP 服务验证 DeepSeek 和 Qwen 请求、鉴权、分片、usage、降级及半途断流；未使用付费外部 Key。

选择 `MOCK_LOCAL` 或未配置 Key 时保留离线模式。有 Key 的远端调用失败且禁用降级时，运行记录必须失败，不使用本地输出代替。当前完整验证记录见仓库 `docs/INTEGRATION_ACCEPTANCE.md`。

## 持久化与权限

- 执行前检查画布访问权限、DAG、账户状态和算力；Kahn 校验同时拒绝重复 Key、悬空连线、孤立节点和非法分支锚点。
- INPUT、KNOWLEDGE、CODE、CONDITION、LLM、OUTPUT 共用同一拓扑执行路径。分支剪枝沿未激活路径传播；汇合节点有任何有效入边即可运行。
- 运行记录、全部节点日志、模型审计与算力扣减使用单事务；用户行锁避免并发扣减丢失与负余额。业务节点错误保存 FAILED 和后续 SKIPPED 日志；持久层错误整笔回滚。
- 默认值与输入按变量类型校验。模板支持 `{{node.field}}`、`{{input.field}}` 和嵌套字段；缺失变量替换为空。
- 运行日志可含私人输入，默认仅执行人或管理员可查询。快照运行仍先校验原工作流访问权限。
- 模板查询与详情仅公开有效工作流；点赞、Fork 需登录。点赞按请求累加（8 表设计无点赞关联表）。Fork 在事务内锁定源模板、完整克隆主从表，再递增源计数；副本为当前用户私有且清空协作者与热度。

## SSE 事件协议

每个事件的 data 使用 `Result<T>` 包装，与普通接口相同。发送顺序：

1. `WORKFLOW_START`：`{execRecordId, execNo}`。
2. 每个有效节点 `NODE_START`：`{nodeKey, nodeType}`。
3. 模型分片 `NODE_TOKEN`：`{nodeKey, chunk}`。
4. `NODE_COMPLETE` / `NODE_ERROR` / `NODE_SKIPPED`：完整节点日志。
5. `WORKFLOW_COMPLETE`：事务提交后的 `ExecRecordDetailVO`，record 状态可能为 SUCCESS 或 FAILED。
6. 事务级异常 `ERROR`：标准错误包装。

控制器将认证上下文保存在本次请求属性中，供 Spring MVC 的 ASYNC 完成派发读取。断开浏览器显示不撤销后台运行，运行结果及算力扣减仍落库；前端提示到运行记录查看结果。

## 验证与交接

- `mvn clean test`：包括 `WorkflowEngineAndMarketTest` 21 项和原 `WorkflowCrudAndCollabTest` 8 项。
- `npm run build`：Vue TypeScript 检查和生产构建。
- VIEW-10～15 的全部 6 个独立视图已接入；全仓库当前共有24个既定视图。浏览器平台验收覆盖 Text-to-Flow、SSE 调试、记录详情和模板 Fork。
- 文档命名冲突：DEVELOPMENT_TASKS 的 TASK-020 使用 `WorkflowEngineIntegrationTest`，TESTING 与本次用户要求使用 `WorkflowEngineAndMarketTest`，本次遵循后两者。真实 API Key 按 FINAL_REVIEW 的默认方案保持可选，离线使用流式沙箱。
