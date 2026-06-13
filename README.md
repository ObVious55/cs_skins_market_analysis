# CS饰品价格预测分析系统


https://github.com/user-attachments/assets/e6475ce3-78f9-4d70-87c5-d68c888c7821


面向 CS2 饰品市场的 AI 分析系统，帮助用户从自然语言问题出发，快速识别目标饰品、分析价格走势，并生成可解释的买入、持有或观望建议，用户持仓分析。

用户不需要准确输入完整饰品名称。
例如输入：

```
传承最近适合入吗？
抽象派后续还有上涨空间吗？
```

系统会结合 RAG 检索、饰品基础数据、价格走势和策略分析，自动定位目标饰品，并给出结构化的市场判断。

## 项目亮点

### 自然语言识别饰品

用户可以直接使用模糊表达或口语化问题，系统会通过 RAG 检索和 TopK 精排，从饰品库中匹配最可能的目标饰品。

支持场景包括：

- 模糊名称查询
- 中文简称查询
- 追问中的“这个”“这把”“它”
- 缺少磨损信息时的候选补全
- 返回候选饰品、置信度和匹配依据

### AI 辅助市场分析

系统会基于饰品价格数据、历史走势和用户问题，生成面向普通用户的分析结果。

分析内容包括：

- 当前价格位置
- 近期趋势判断
- 风险提示
- 买入、持有或观望建议
- 适合短线还是长期关注
- 结论背后的数据依据

本项目的目标不是替代用户决策，而是把复杂的饰品市场数据转化成更容易理解的判断依据。

### 策略引擎

项目内置可扩展的策略分析框架，可以对单个饰品运行多种交易/观察策略。

当前支持通过 YAML 定义策略规则，例如：

- 缩量回踩
- 底部放量
- 均线金叉
- 箱体震荡
- 放量突破

策略分析结果会被聚合为统一的信号，用于辅助 AI 生成更稳定、更有依据的市场建议。

### 对话式追问

系统支持连续追问。

例如用户第一次问：

```
帮我看看 AK 传承能不能买
```

之后继续问：

```
那现在适合入吗？
风险大不大？
如果跌了应该看到什么位置？
```

系统会结合 Redis 中的上下文记录，理解用户仍然在讨论同一个饰品。

### 持仓分析

用户在项目里登录自己的Steam账号，Agent能够查询用户的库存，进行持仓的分析




## 快速开始（Windows / PowerShell）

先决条件：

- JDK 17+（或与 pom.xml 兼容的 Java 版本）
- Maven（项目内含 `mvnw`，可直接使用）
- Redis、MySQL（可选：若需要完整功能）、Qdrant（若使用向量 DB）
- Embedding 服务（当前 application.yml 默认使用 Ollama）

在项目根目录下运行（PowerShell）：

```powershell
# 使用 Maven Wrapper 构建并运行
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd spring-boot:run

# 或运行打包后的 jar
java -jar target\price-prediction-*.jar
```

默认服务端口：3000（可通过环境变量 SERVER_PORT 修改）


## 必要的环境变量（可在 application.yml 中通过占位符设置）

在运行前请确保以下重要环境变量可用：

- OPENAI_BASE_URL（或 app.openai.base-url）: AI 服务地址（示例: https://api.deepseek.com）
- OPENAI_API_KEY（或 app.openai.api-key）: AI 服务 Key
- CSQAQ_BASE_URL / CSQAQ_API_TOKEN：第三方饰品数据 API（可选）
- REDIS_HOST / REDIS_PORT / REDIS_DATABASE：Redis 连接配置
- DB_URL / DB_USERNAME / DB_PASSWORD：MySQL（若需要 cs_qaq_item_id 表）
- QDRANT 配置（若使用 qdrant）在 application.yml 里
- embedding.provider 与 provider-specific 配置（application.yml 默认：ollama）

举例（PowerShell 设置环境变量示例，临时）：

```powershell
$env:OPENAI_API_KEY = "your_api_key_here"
$env:OPENAI_BASE_URL = "https://api.deepseek.com"
$env:REDIS_HOST = "localhost"
$env:DB_URL = "jdbc:mysql://localhost:3306/price_prediction?useSSL=false&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_db_password"
```



## 主要 HTTP 接口

1) /rag/refine

- 方法：GET
- 说明：对用户自然语言查询进行 RAG TopK 精排，返回候选及置信度，便于在 APIFOX 验证 query 优化效果。
- 参数：
  - q (必填)：用户查询，例如 "这把蝴蝶刀能不能买"
  - topK (可选)：用于向量检索的 topK，默认 10
- 示例：
  GET http://localhost:3000/rag/refine?q=这把蝴蝶刀能不能买&topK=50

返回示例（JSON）包含：primaryItemId、primaryName、confidence、candidates（每个候选包含 vectorScore / lexicalScore / metadata / finalScore）


2) /api/ai/chat

- 方法：POST
- 说明：主对话接口，支持首次发起分析与追问（follow-up）。
- 请求体（JSON）：
  - steamId (string) - 用户 ID（强烈建议在每次请求都传入）
  - message (string) - 用户消息（首次或追问）
  - isFollowUp (boolean) - 是否为追问
  - userQuery (string) - （可选）自然语言查询，用于后端基于 RAG 优化并替换 itemName
  - priceData (object) - （首次请求可选）若包含则表示前端已预先拉取并传入价格快照

- 示例（首次）：

```json
{
  "steamId": "76561198754572993",
  "message": "请帮我分析这个饰品：USP 消音版 | 枪响人亡 (崭新出厂)（ID: 6558）",
  "isFollowUp": false,
  "priceData": { /* 从平台拉取的 priceData */ }
}
```

- 示例（追问，带 header 回退或直接 body 传 steamId）：

```json
{
  "steamId": "76561198754572993",
  "message": "现在还能买入吗？",
  "isFollowUp": true,
  "userQuery": "这把 USP 值不值买"
}
```

注意：为避免出现 "用户: null, 内容: null" 的日志问题，请确保追问请求包含 `steamId` 或在请求头添加 `X-User-Id`（服务端会回退读取 header 中的用户 id）。推荐始终在请求体传入 `steamId`。

## 饰品数据来源

csqaq.com


## 开发与调试建议

- 若需要在本地快速调试 RAG，可使用小规模的 item catalog 并把 `cs.item.import-enabled` 与 `cs.item.rag-index-enabled` 设置为 true，然后启动索引导入脚本
- 建议把 TopKQueryOptimizer 的权重/阈值提取为配置项（application.yml），便于 A/B 测试
- 在生产环境开启更多日志（或把 refine 结果记录到专门的监控索引）以便离线标注与持续优化


## 策略（Strategies）与 Strategy Engine

项目内置了一套可扩展的策略框架，用于对单个饰品运行规则/策略集并产出合成的买卖信号（Strategy Analysis）。主要实现要点如下：

- 策略定义：位于 `strategies/` 目录下的 YAML 文件（例如 `bottom_volume.yaml`），每个文件包含策略名称、描述、规则要点与执行提示。你可以通过新增 YAML 文件实现新的策略。
- StrategyEngine：Java 端的策略执行器，负责把策略 YAML 装载为可执行策略（StrategySkill），运行历史价格 / 成交数据（通过 `ApiDataService` 提供）并返回 `StrategyAnalysisResult`。
- ta4j：对于需要回测或以时间序列指标为基础的策略，项目在策略引擎内部使用 ta4j（或 ta4j 风格的指标计算）来计算指标、信号并对 K 线/时间序列进行分析。

主要的 Java 类型与说明：

- `StrategyEngine`：策略执行入口，方法示例 `StrategyAnalysisResult analyze(String itemId)`。引擎负责加载对应策略（YAML + StrategyContext）、运行 Skill，并聚合 Signals 为最终决策。
- `StrategyAnalysisResult`：策略输出 DTO，通常包含字段：`itemId`、`finalSignal`（BUY/HOLD/SELL）、`finalScore`（数值化分数）、`riskLevel`、`summary`、`signals`（各策略子信号明细）等。
- `StrategySkill` / YAML 配置：每个 YAML 描述一组规则（如：趋势判定、成交放量、阳线条件、风险排查）以及执行时所需的工具（例如：`get_item_daily_history`、`get_item_realtime_quote`）。

AiTools 中的策略工具（Agent skill）

为了让 Agent 能够在对话中调用策略分析，项目在 `AiTools` 中暴露了一个 Tool：

- `@Tool(name = "runItemStrategyAnalysis")` —— 方法签名 `public StrategyAnalysisResult runItemStrategyAnalysis(String itemId)`。

该 Tool 的行为：

- 接收 `itemId`（或可调整为接收 market_hash_name 并在内部查映射），调用 `StrategyEngine.analyze(itemId)` 并返回 `StrategyAnalysisResult`，对传入参数做基本校验并在异常时返回可读的默认 `StrategyAnalysisResult`。

如何在 Agent / Langchain4j 场景中使用：

1. 在 agent 的工具清单中注册 `AiTools`（项目已有集成），Agent 在推理过程中可以以自然语言触发 `runItemStrategyAnalysis`（Tool 名称即上面的 name）。
2. Agent 在工具返回后会把 `StrategyAnalysisResult` 的文本/结构插入到对话中，作为判断与建议的一部分。

示例：直接在代码中调用（非 Agent）

```java
// 注入 AiTools 或直接注入 StrategyEngine
StrategyAnalysisResult res = aiTools.runItemStrategyAnalysis("6558");
// 或
StrategyAnalysisResult res2 = strategyEngine.analyze("6558");

System.out.println(res.getSummary());
```

如何添加/调试新的策略

1. 在 `strategies/` 下新增 YAML文件，参考已存在的模板（例如 `bottom_volume.yaml`）说明规则、必需的数据源与工具。
2. 若策略需要新的工具（如额外的 price/volume API），在 `AiTools` 中新增对应 `@Tool` 方法并实现数据获取逻辑（工具应返回字符串或结构化对象，方便 Agent 使用）。
3. 在 StrategyEngine 中注册新策略（若引擎使用自动加载则放入目录后重启服务即可）。
4. 添加单元测试：模拟 priceData / kline 数据并断言 `StrategyAnalysisResult` 的关键字段（finalSignal / finalScore / signals）。

配置建议

- 将策略权重、ta4j 的回测参数、以及 TopKQueryOptimizer 的合并权重抽取到 `application.yml` 中，以方便线上调整与 A/B 测试。
- 在生产环境中对策略分析调用做限流与缓存（某些 item 的策略分析较重，建议对相同 itemId 的分析结果缓存 1–5 分钟）。

安全与可解释性

- 策略分析需要保证不可随意生成虚假的价格/成交数据。StrategySkill 在生成结论时应始终包含“依据数据来源”的说明（例如：基于 BUFF 近 30 日成交/在售数）。
- 推荐在 `StrategyAnalysisResult.summary` 中增加 `explain` 字段或在 `signals` 中详细列出触发规则，便于审计与用户展示。

## 项目结构

```
src/main/java/com/example/priceprediction
├── controller      # HTTP 接口
├── service         # 核心业务逻辑
├── rag             # RAG 检索与 Query 优化
├── strategy        # 策略引擎
├── repository      # 数据访问层
├── entity          # 数据库实体
├── dto             # 请求与响应对象
└── config          # 配置类
```

策略配置位于：

```
src/strategies/
```

## 贡献指南

欢迎提交 PR：

- Fork 本仓库
- 新建 feature 分支
- 编写单元测试（或调试脚本）并提交
- 提交 PR 描述变更动机与向后兼容性说明


## 许可证

默认使用 MIT 许可证（如需其他许可证请替换此处）

```
MIT License
```


---

## Docker

Prepare the local environment file first:

```powershell
copy env.example .env
```

Edit `.env` and fill in real API keys, tokens, and database password. Then start the stack:

```powershell
docker compose up --build -d
```

The compose stack starts the Spring Boot app, MySQL, Redis, and Qdrant. The app is exposed at:

```text
http://localhost:3000
```

Stop the stack:

```powershell
docker compose down
```

https://github.com/user-attachments/assets/1509a445-269d-4658-81c3-8b7eb27a1001

