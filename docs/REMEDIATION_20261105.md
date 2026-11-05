# FlowMind 2026年11月5日问题整改记录

本轮按用户列出的12项问题核对仓库并修复可直接落地的缺陷。身份信息已经确认；文档、种子审计和前端加载已调整，并修复了浏览器 SSE 运行非法图时误变成401的问题。当前仍为规则生成图、单进程协同、Markdown词匹配知识库；这些能力没有因文档修订变成模型规划、分布式协同或向量检索。真实评委现场演示尚未完成。

## 逐项处理

| 项 | 处理与当前状态 | 后续条件 |
| --- | --- | --- |
| 1 学号组号 | 已根据用户提供的信息填写两份 FINAL_REVIEW，并同步第一阶段 Markdown / Word 报告：23级计算机科学与技术1组，顾峰鸣238111541112、张誉诚238111541129、郝唐鑫238111541113、李俊豪238111541116 | 最终报告名为 `23级计算机科学与技术1组顾峰鸣238111541112Web应用综合实训报告.docx`；本轮没有把第一阶段报告冒名为最终报告 |
| 2 七步现场演示 | 新增 DEFENSE_RUNBOOK，写明四人分工、输入、观察点、失败处理与签字表。新增浏览器关键路径回归及证据截图；发现并修复非法图通过SSE运行时401跳登录的缺陷 | 四位成员必须在实际演示机、实际MySQL库连续走完，填写实际结果；自动化预演不代替人工现场验收 |
| 3 注册契约 | SRS与API对齐：公开注册仅创建STUDENT，额外roleType返回400 / 40000；教师由管理员分配。并纠正SRS的教师注册赠额度与默认JWT有效期（实际2小时） | 已加入浏览器接口断言，保留原权限边界 |
| 4 审计种子 | DATABASE、权威初始化SQL和H2适配SQL统一改为默认词 `违法违禁`，不再把drop table当默认拦截词 | 已有数据库的历史审计不重写。现场按新账号和时间筛选本次实际拦截 |
| 5 历史报告 | 第一阶段Markdown和Word已修订，去掉未使用的LiteFlow、Spring AI、Monaco、Knife4j等实现宣称；修正VIEW-03归属、注册、图表、缓存描述。Word导出8页并逐页查看 | 虚拟线程确实用于WfEngineController的SSE执行任务，保留这一准确用途，不宣称WebSocket全面虚拟线程化 |
| 6 规则生成 | SRS、API、总览、业务说明统一说明当前generationMode为RULE，Key仅影响LLM执行；三示例图分别4/7/3节点，规则生成标签已有浏览器验证 | 真实模型规划暂未实现。若另立扩展任务，应先定义结构化schema、调用预算/审计、图与配置白名单、拓扑验证、失败回退策略，再以合法图标MODEL |
| 7 单实例协同 | 架构图删除“Redis管理房间”的错误暗示；补充单进程房间和Yjs写MySQL边界；浏览器验证全部页面关闭后坐标恢复 | 演示固定单实例；异常丢失约2秒仅是正常链路下的快照间隔估计。多实例需房间单一写入者、实例间广播、版本并发控制和重启恢复，单靠共享数据库或粘性会话不够 |
| 8 知识库 | 业务文档去掉BM25和向量折算说法，明确Markdown词匹配、来源文件名、标题加分，检索配额固定64 | 当前没有嵌入或向量检索；课程演示如实说明。扩展时须另定语料、嵌入模型、索引更新与检索准确性验收，避免为了术语添加中间件 |
| 9 密码密钥 | 已核实非test/mysql-test环境拒绝默认JWT密钥且至少32字节；README已有随机密钥配置，演示手册再次落实 | 本轮未连接部署库改密码。演示账号123456和管理员重置123456只适合本地；对外部署前需独立密码、停用演示账号，并修改重置流程后另做上线验收 |
| 10 旧MySQL补列 | 现有幂等迁移已在真实MySQL用例中补跑：缺列故障复现、部分升级、重复执行、状态及版本不被覆盖、列表恢复 | 用户旧业务库仍需备份后执行补列脚本；本轮仅测试随机隔离库，没有擅自连接旧业务库 |
| 11 前端体积 | 新增构建插件unplugin-vue-components，按需导入组件、指令及样式；移除全局ElementPlus注册和整包CSS；中文locale通过ConfigProvider。移除强制element单包 | 原949.28kB包消失，最大组件共享块238.42kB；charts仍607.04kB，阈值从1000降为650kB；不把体积警告阈值当成性能结论 |
| 12 MySQL未跑 | 本轮重新执行隔离MySQL8.0.42验收，3项全部执行通过，0失败/错误/跳过 | 浏览器仍使用H2隔离后端，未声称浏览器直连实际MySQL通过；人工演示须使用实际MySQL库 |

## 新发现并修复的演示缺陷

浏览器发送 `Accept: text/event-stream` 运行含环图时，执行前抛出业务异常。原全局异常响应未指定Content-Type，响应媒体类型协商失败后进入错误分派，最终返回401，前端误以为登录失效。修复为全局异常响应显式返回application/json；浏览器现得到HTTP400 / 40005，页面显示循环依赖，登录仍有效，也不新增成功运行记录。该路径由defense.spec.ts在实际HTTP服务上回归，覆盖MockMvc此前没有发现的媒体类型问题。

## 本轮实测

| 检查 | 本轮结果 | 验证边界 |
| --- | --- | --- |
| `mvn clean test -B` | 70项，67通过、0失败/错误、3跳过 | 跳过的3项是需显式启用的MySQL用例；测试包含已有安全、引擎、协同、教务回归 |
| `scripts/test_mysql.ps1` | MySQL8.0.42，3项通过、0失败/错误/跳过 | 临时实例与随机验收库；8表、JSON、外键、升级与业务闭环，未接触现有业务库 |
| `npm run build` | 退出0，Vue/TypeScript及Vite生产构建通过 | 607.04kB图表块保留，组件和样式改为按需加载 |
| 生产产物Playwright | 7组通过，0重试 | Edge访问Vite preview的dist产物，后端H2；新增安全、三图、条件、环路与监控增量，原有协同/教务闭环继续执行 |
| 测试TS独立检查 | 退出0 | 覆盖tests/*.ts与playwright.config.ts，不只检查生产src |
| 视图数量与路径 | 恰好24个.vue | 未增加或合并业务视图 |
| 第一阶段Word版式 | 8页逐页查看 | 指定render_docx.py因本机缺少LibreOffice未能工作，改用已安装Word导出PDF后Poppler转PNG；清理标题下横线、孤立标题与拆行溢页，未交付内部渲染文件 |

## 复现与证据

按README准备JDK21、Maven、Node与Microsoft Edge，8080/5173端口空闲。

```powershell
cd D:\FlowMind\flowmind-backend
mvn clean test -B
./scripts/test_mysql.ps1 -MySqlBin 'D:\MYSQL\MySQL Server 8.0\bin'

cd D:\FlowMind\flowmind-frontend
npm ci
npm run build
$env:FLOWMIND_E2E_PREVIEW = 'true'
try { npm run test:e2e } finally { Remove-Item Env:FLOWMIND_E2E_PREVIEW }
$taskTestFiles = @(rg --files tests -g '*.ts')
& ./node_modules/.bin/tsc.cmd --noEmit --target ES2022 --module ESNext --moduleResolution Bundler --types node --skipLibCheck @taskTestFiles playwright.config.ts
```

本次日志保存在backend-review.log、target/mysql-acceptance-20261105.log、前端build-review.log和e2e-production-review.log；这些运行产物被Git忽略，重复运行会覆盖。预演截图与不含Token的检查JSON已复制至 `artifacts/defense-20261105/`，仅代表自动化预演；截图对应业务在测试后已清理。人工现场记录仍保留“未现场验收”，见DEFENSE_RUNBOOK。

项目组长仍需完成：实际演示机七步签字、已有业务库补列（如缺失），以及课程最终报告写作；公网部署、真实厂商Key和多实例/向量/模型规划扩展均未在本轮完成。
