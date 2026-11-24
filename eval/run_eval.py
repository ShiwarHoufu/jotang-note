#!/usr/bin/env python3
"""用 RAGAS 给 EvalRunner 导出的 run.jsonl 打分。"""

import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from langchain_openai import OpenAIEmbeddings
from openai import AsyncOpenAI
from ragas import evaluate
from ragas.cache import DiskCacheBackend
from ragas.dataset_schema import EvaluationDataset
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas.llms import llm_factory
from ragas.metrics import (
    AnswerRelevancy,
    Faithfulness,
    LLMContextPrecisionWithReference,
    LLMContextRecall,
)
from ragas.run_config import RunConfig

EVAL_DIR = Path(__file__).resolve().parent
INPUT_PATH = EVAL_DIR / "out" / "run.jsonl"
REPORT_PATH = EVAL_DIR / "out" / "report.csv"
SUMMARY_PATH = EVAL_DIR / "out" / "summary.json"
CACHE_DIR = EVAL_DIR / ".cache"


def require_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        sys.exit(f"缺少环境变量 {name}：写入 eval/.env 或当前 shell 后再运行")
    return value


def load_rows(path: Path) -> list[dict]:
    if not path.exists():
        sys.exit(f"找不到 {path}：先用 local,eval profile 跑一次 EvalRunner")

    rows = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        for field in ("user_input", "response", "reference"):
            if not row.get(field):
                sys.exit(f"{path.name} 第 {line_no} 行缺少 {field}")
        contexts = row.get("retrieved_contexts")
        if not isinstance(contexts, list):
            sys.exit(f"{path.name} 第 {line_no} 行的 retrieved_contexts 不是数组")
        # 空 contexts 是合法样本：说明这一轮没检索到东西，指标会反映出来
        row["retrieved_contexts"] = contexts
        rows.append({k: row[k] for k in ("user_input", "retrieved_contexts", "response", "reference")})

    if not rows:
        sys.exit(f"{path} 里没有样本")
    return rows


def build_judge() -> object:
    client = AsyncOpenAI(
        api_key=require_env("DEEPSEEK_KEY"),
        base_url=os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
    )
    return llm_factory(
        os.environ.get("JUDGE_MODEL", "deepseek-flash"),
        provider="openai",
        client=client,
        temperature=0.0,
        # 长回答的 statement 列表会撑爆输出上限，DeepSeek 侧报 finish_reason=length
        max_tokens=16384,
        cache=DiskCacheBackend(cache_dir=str(CACHE_DIR)),
    )


def build_embeddings() -> object:
    # 旧指标类调用 embed_documents/embed_query；embedding_factory 返回的新接口没有这两个方法，
    # 所以这里显式走 LangChain 包装器。check_embedding_ctx_length=False 避免按 OpenAI 分词发送。
    return LangchainEmbeddingsWrapper(OpenAIEmbeddings(
        model=os.environ.get("EMBEDDING_MODEL", "BAAI/bge-m3"),
        api_key=require_env("SILICONFLOW_KEY"),
        base_url=os.environ.get("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1"),
        check_embedding_ctx_length=False,
    ))


def main() -> None:
    load_dotenv(EVAL_DIR / ".env")
    rows = load_rows(INPUT_PATH)

    metrics = [
        Faithfulness(),
        AnswerRelevancy(),
        LLMContextRecall(),
        LLMContextPrecisionWithReference(),
    ]
    result = evaluate(
        EvaluationDataset.from_list(rows),
        metrics=metrics,
        llm=build_judge(),
        embeddings=build_embeddings(),
        # 16 个并发会触发 DeepSeek 排队超时；4 个并发稳定，缓存能抵消重跑成本
        run_config=RunConfig(timeout=300, max_workers=4),
        raise_exceptions=False,
        show_progress=True,
    )

    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    frame = result.to_pandas()
    frame.to_csv(REPORT_PATH, index=False, encoding="utf-8-sig")

    summary = {"samples": len(frame)}
    for metric in metrics:
        if metric.name not in frame.columns:
            continue
        scores = frame[metric.name]
        summary[metric.name] = round(float(scores.mean()), 4)
        summary[f"{metric.name}_failed"] = int(scores.isna().sum())
    summary["empty_context_samples"] = sum(1 for row in rows if not row["retrieved_contexts"])
    SUMMARY_PATH.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"\n明细：{REPORT_PATH}")
    print(f"汇总：{SUMMARY_PATH}")


if __name__ == "__main__":
    main()
