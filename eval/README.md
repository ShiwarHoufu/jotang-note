# RAG 离线评测

Java 侧用真实对话链路导出样本，Python 侧用 RAGAS 打分，两边只通过 `out/run.jsonl` 交接。

## 1. 导出样本（Java）

IDEA Run Configuration 的 Active profiles 填 `local,eval`，工作目录保持 `jotang-ai-app`。
`EvalRunner` 会逐题调用 `ChatService.answerForEval`（空历史、不限流、不落库、temperature 0），
输出 `eval/out/run.jsonl` 后进程退出。

命令行等价方式（先确保 Nacos、MySQL、Qdrant 已启动）：

```powershell
cd D:\projects\jotang\jotang-note
.\mvnw.cmd -pl jotang-ai-app -am install -DskipTests
cd jotang-ai-app
..\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,eval"
```

## 2. 打分（Python）

```powershell
cd D:\projects\jotang\jotang-note\eval
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
# 编辑 .env，填入 DEEPSEEK_KEY、SILICONFLOW_KEY
.\.venv\Scripts\python.exe run_eval.py
```

用 `.venv\Scripts\python.exe` 直接调用可绕开 PowerShell 执行策略对 `Activate.ps1` 的限制。

产物：

| 文件 | 内容 |
|---|---|
| `out/report.csv` | 每题回答、上下文与各指标得分 |
| `out/summary.json` | 各指标均值、样本数、空检索样本数 |
| `.cache/` | judge 调用缓存，重跑同一批题不重复花钱 |

## 3. 指标与字段

| 指标 | 需要字段 | 说明 |
|---|---|---|
| `faithfulness` | response + contexts | 回答是否有上下文支撑 |
| `answer_relevancy` | user_input + response | 是否答到点上，需要 embedding |
| `context_recall` | contexts + reference | 标准答案里的信息是否被检索到 |
| `llm_context_precision_with_reference` | contexts + reference | 相关块是否排在前面 |