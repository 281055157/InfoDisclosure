# 信息披露审核系统核心代码

这是一个面向理财产品信息披露附件的智能审核系统核心代码导出版本。项目提供从文件接收、文档解析、产品匹配、规则审核，到大模型语义复核、证据验证、人工审核和规则反馈治理的完整代码骨架，适合作为独立 GitHub 仓库继续开发。

## 能力范围

- PDF 正文分页解析、Excel 参数表元数据读取和文件声明信息解析
- 基于产品代码、份额代码、别名、名称和产品系列的产品匹配
- `REGEX`、`REQUIRED`、`ENUM_MAPPING`、`NUMERIC_RANGE`、Java 插件、`LLM_POLICY`、`HYBRID` 等规则执行器
- RabbitMQ 驱动的分阶段审核流水线，以及可持久化的审核上下文
- OpenAI-compatible 模型供应商适配、重试、降级、调用记录和 Token 用量统计
- LLM 主审核与语义规则批量合并、超预算分页回退、证据原文回查和风险合并
- 人工通过、警告通过、退回、拒绝、误报反馈和人工补充问题
- 规则反馈治理：分组、聚合运行、候选规则、语义回测、提案和审核链路追踪
- Vue 3 工作台、任务详情、规则管理、模型链路、Token 明细和治理链路视图

## 技术栈

- 后端：Java 17、Spring Boot、Spring MVC/WebFlux、Spring Data JPA、Flyway
- 消息与数据：RabbitMQ、PostgreSQL（测试可使用 H2）
- 文档处理：Apache PDFBox、Apache POI、RE2/J
- 模型调用：WebClient、供应商适配器、模型重试/降级和结构化 JSON 响应
- 反馈治理：LangGraph4j 编排、持久化执行上下文、调用链路和 Token 统计
- 前端：Vue 3、Vite、Lucide、Playwright

## 目录结构

```text
github-core/
├─ pom.xml
├─ src/
│  ├─ main/java/                         # 后端领域、接口、任务、规则、模型和治理代码
│  ├─ main/resources/db/migration/       # 数据库表结构与演进脚本
│  ├─ main/resources/prompts/             # 审核与反馈治理提示词模板
│  └─ main/resources/products.json        # 不含真实业务数据的通用产品库示例
├─ src/test/java/                         # 后端单元测试与集成测试
├─ src/test/resources/                    # 测试配置和 PDF 生成字体
└─ frontend/
   ├─ src/                                # Vue 页面、组件和 API 调用
   ├─ tests/                              # Playwright 自动化测试
   └─ package.json                        # 前端依赖和脚本
```

## 核心设计

### 分阶段审核

消息只携带任务标识、事件标识和阶段信息。解析、声明识别、产品匹配、规则审核、LLM 审核、证据验证、结果合并和持久化分别由阶段处理器完成；阶段产物写入审核上下文和业务表，避免将正文等大对象放入消息队列。

### 成本可控的模型调用

主审核与语义规则优先合并为一次结构化请求。长文档按照字符预算和页面窗口拆分，部分窗口失败时保留可用结果。每次尝试都记录任务、阶段、操作、规则、窗口、供应商、模型、状态、耗时和输入/输出/缓存命中 Token，并保留实际成功模型。

### 反馈治理闭环

人工反馈先保存问题快照、规则版本、文档类型、产品代码、来源和处理状态，再经过分组、聚合分析、候选规则回测和提案审核。LLM 规则回测采用分层样本、批量请求和证据验证；回测无效或样本不足时不会生成可提交的规则提案。

