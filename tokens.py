"""tokens.py

End-to-end script to build a better estimator for Anthropic token counts
than the rough `p50k_base` approximation.

Features:
- `collect`  mode: query Anthropic's messages.count_tokens API to build a
  dataset of (text, anthropic_token_count).
- `train`    mode: train simple ML models (linear + gradient boosting)
  to predict Anthropic counts from tiktoken p50k_base + cheap text features.
- `estimate` mode: use the trained model to estimate tokens for new text.

Dependencies (install with pip):
    pip install anthropic tiktoken scikit-learn joblib

Environment:
- Requires ANTHROPIC_API_KEY to be set in your environment.

Example usage:

    # 1) Collect data from a file of prompts (one per line)
    python tokens.py collect \
        --model claude-sonnet-4-5-20250929 \
        --input prompts.txt \
        --output dataset.jsonl

    # 2) Train models from the collected dataset
    python tokens.py train \
        --dataset dataset.jsonl \
        --model-out token_estimator.joblib

    # 3) Estimate tokens for a new text
    python tokens.py estimate \
        --model-in token_estimator.joblib \
        --text "Hello from my estimator!"

Or just run: python tokens.py (or uv run tokens.py) which will use a built-in set of synthetic prompts and a default model to train an estimator and save it to `token_estimator.joblib`.

This script is intentionally simple and opinionated so you can tweak it
for your own workflows.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from typing import Iterable, List

import joblib
import numpy as np
import tiktoken
from anthropic import Anthropic
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.linear_model import LinearRegression

DEFAULT_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-latest")

DEFAULT_PROMPTS: List[str] = [
    "Hello!",
    "Explain how BPE tokenization works in simple terms.",
    "Write a short paragraph about large language models and their applications in industry.",
    "def hello():\n    print(\"hi\")",
    "{\"event\": \"login\", \"user\": 123, \"success\": true}",
    "这是一个包含中文字符的句子。",
    "これは日本語の文章です。",
    "Here is a long-ish prompt with numbers 1234567890 and punctuation!!!???",
    "😊✨🔥 Emojis mixed with text to probe non-ASCII handling.",
    "A line with\nmultiple\nnewlines to check behavior.",
    "https://example.com/some/very/long/path?with=query&string=parameters",
    "Repeat repeat repeat repeat repeat repeat repeat repeat repeat repeat.",
    "{\"nested\": {\"json\": [1, 2, 3, 4, 5]}, \"ok\": true}",
    "SELECT * FROM users WHERE created_at > NOW() - INTERVAL '7 days';",
    "Short",
    "Another medium-length sentence for token estimation experiments.",
]


# -----------------------------
# Anthropic API helpers
# -----------------------------


def get_anthropic_client() -> Anthropic:
    """Create an Anthropic client using ANTHROPIC_API_KEY.

    Raises a clear error if the key is missing.
    """

    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise RuntimeError(
            "ANTHROPIC_API_KEY is not set in the environment. "
            "Export it before running this script."
        )
    return Anthropic(api_key=api_key)


def anthropic_count_tokens(text: str, model: str) -> int:
    """Call Anthropic's messages.count_tokens to get the input token count.

    We use a single user message with the given text.
    """

    client = get_anthropic_client()
    resp = client.messages.count_tokens(
        model=model,
        messages=[{"role": "user", "content": text}],
    )
    # `resp` is a typed object, but we only care about input_tokens
    return int(resp.input_tokens)


# -----------------------------
# Feature engineering
# -----------------------------


@dataclass
class TextExample:
    text: str
    anthropic_tokens: int


# Use p50k_base as the baseline tokenizer
P50K_ENCODER = tiktoken.get_encoding("p50k_base")


def p50k_count(text: str) -> int:
    return len(P50K_ENCODER.encode(text))


def extract_features(text: str) -> np.ndarray:
    """Compute cheap features for the regression model.

    Features (in order):
    - p50k token count
    - n_chars (length in characters)
    - n_bytes (length in UTF-8 bytes)
    - n_spaces
    - n_newlines
    - frac_non_ascii
    - frac_digits
    """

    s = text
    p = p50k_count(s)
    n_chars = len(s)
    utf8 = s.encode("utf-8")
    n_bytes = len(utf8)
    n_spaces = s.count(" ")
    n_newlines = s.count("\n")
    n_non_ascii = sum(1 for ch in s if ord(ch) > 127)
    n_digits = sum(ch.isdigit() for ch in s)

    denom = max(1, n_chars)
    frac_non_ascii = n_non_ascii / denom
    frac_digits = n_digits / denom

    return np.array(
        [
            p,
            n_chars,
            n_bytes,
            n_spaces,
            n_newlines,
            frac_non_ascii,
            frac_digits,
        ],
        dtype=float,
    )


def build_feature_matrix(examples: List[TextExample]) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Build X, y, and baseline p50k counts from examples.

    X: feature matrix
    y: Anthropic token counts
    p: p50k counts (for diagnostics)
    """

    X_list: List[np.ndarray] = []
    y_list: List[int] = []
    p_list: List[int] = []

    for ex in examples:
        X_list.append(extract_features(ex.text))
        y_list.append(ex.anthropic_tokens)
        p_list.append(p50k_count(ex.text))

    X = np.vstack(X_list)
    y = np.array(y_list, dtype=float)
    p = np.array(p_list, dtype=float)
    return X, y, p


# -----------------------------
# Models
# -----------------------------


@dataclass
class TokenEstimatorModels:
    linear: LinearRegression
    gbr: GradientBoostingRegressor


def train_models(examples: List[TextExample]) -> TokenEstimatorModels:
    """Train a linear model and a gradient boosting model.

    The idea:
    - LinearRegression learns a simple global correction on top of p50k and
      other features (often already quite good).
    - GradientBoostingRegressor captures non-linear effects for more accuracy.
    """

    X, y, _ = build_feature_matrix(examples)

    linear = LinearRegression()
    linear.fit(X, y)

    gbr = GradientBoostingRegressor()
    gbr.fit(X, y)

    return TokenEstimatorModels(linear=linear, gbr=gbr)


def save_models(models: TokenEstimatorModels, path: str) -> None:
    joblib.dump(models, path)


def load_models(path: str) -> TokenEstimatorModels:
    return joblib.load(path)


def predict_tokens(models: TokenEstimatorModels, text: str) -> dict:
    """Return a dict with baseline and model predictions for a given text."""

    feats = extract_features(text).reshape(1, -1)
    baseline = p50k_count(text)
    linear_pred = float(models.linear.predict(feats)[0])
    gbr_pred = float(models.gbr.predict(feats)[0])

    # Round to int and clamp at zero
    def clean(x: float) -> int:
        return max(0, int(round(x)))

    return {
        "p50k": int(baseline),
        "linear": clean(linear_pred),
        "gbr": clean(gbr_pred),
    }


# -----------------------------
# Data collection & evaluation
# -----------------------------


def read_texts_from_file(path: str) -> List[str]:
    """Read one text per line from a file.

    If you need multi-line texts, adjust this to your own format.
    """

    with open(path, "r", encoding="utf-8") as f:
        return [line.rstrip("\n") for line in f if line.strip()]


def write_jsonl(examples: Iterable[TextExample], path: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        for ex in examples:
            obj = {
                "text": ex.text,
                "anthropic_tokens": ex.anthropic_tokens,
            }
            f.write(json.dumps(obj, ensure_ascii=False) + "\n")


def read_jsonl(path: str) -> List[TextExample]:
    examples: List[TextExample] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            examples.append(
                TextExample(
                    text=obj["text"],
                    anthropic_tokens=int(obj["anthropic_tokens"]),
                )
            )
    return examples


def collect_dataset_from_texts(model: str, texts: List[str], max_rpm: int = 100) -> List[TextExample]:
    """Collect (text, anthropic_token_count) pairs into a list of TextExample.

    Respects a rate limit of max_rpm (max requests per minute).
    """
    import time

    client = get_anthropic_client()
    examples: List[TextExample] = []

    delay = 60.0 / max_rpm if max_rpm > 0 else 0.0
    last_call = 0.0

    for i, text in enumerate(texts, start=1):
        now = time.time()
        elapsed = now - last_call
        if elapsed < delay:
            time.sleep(delay - elapsed)
        resp = client.messages.count_tokens(
            model=model,
            messages=[{"role": "user", "content": text}],
        )
        count = int(resp.input_tokens)
        ex = TextExample(text=text, anthropic_tokens=count)
        examples.append(ex)
        last_call = time.time()
        print(f"[{i}/{len(texts)}] tokens={count} text preview={text[:60]!r}")

    return examples


def collect_dataset(model: str, input_path: str, output_path: str, max_rpm: int = 100) -> None:
    texts = read_texts_from_file(input_path)
    examples = collect_dataset_from_texts(model=model, texts=texts, max_rpm=max_rpm)
    write_jsonl(examples, output_path)
    print(f"\nSaved {len(examples)} examples to {output_path}")


def evaluate_baseline_vs_models(
    examples: List[TextExample],
    models: TokenEstimatorModels,
    label: str = "dataset",
) -> None:
    """Print simple error stats comparing p50k, linear, and GBR estimators."""

    X, y, p = build_feature_matrix(examples)

    def mae(pred: np.ndarray) -> float:
        return float(np.mean(np.abs(pred - y)))

    def rel_mae(pred: np.ndarray) -> float:
        nonzero = y > 0
        return float(np.mean(np.abs(pred[nonzero] - y[nonzero]) / y[nonzero]))

    baseline_pred = p
    linear_pred = models.linear.predict(X)
    gbr_pred = models.gbr.predict(X)

    print(f"\nError metrics ({label}):")
    print("Baseline p50k     : MAE = %.2f, rel_MAE = %.2f%%" % (mae(baseline_pred), rel_mae(baseline_pred) * 100))
    print("Linear estimator  : MAE = %.2f, rel_MAE = %.2f%%" % (mae(linear_pred), rel_mae(linear_pred) * 100))
    print("GBR estimator     : MAE = %.2f, rel_MAE = %.2f%%" % (mae(gbr_pred), rel_mae(gbr_pred) * 100))


def split_examples(
    examples: List[TextExample],
    test_fraction: float = 0.2,
    seed: int = 42,
) -> tuple[List[TextExample], List[TextExample]]:
    """Shuffle and split examples into (train, test) lists."""
    if not examples:
        return [], []

    rng = np.random.default_rng(seed)
    indices = np.arange(len(examples))
    rng.shuffle(indices)

    split_idx = int(round(len(examples) * (1.0 - test_fraction)))
    train_idx = indices[:split_idx]
    test_idx = indices[split_idx:]

    train_examples = [examples[i] for i in train_idx]
    test_examples = [examples[i] for i in test_idx]
    return train_examples, test_examples


def run_default_workflow() -> None:
    print(f"Using default model: {DEFAULT_MODEL}")
    examples = collect_dataset_from_texts(
        model=DEFAULT_MODEL,
        texts=DEFAULT_PROMPTS,
        max_rpm=100,
    )
    models = train_models(examples)
    evaluate_baseline_vs_models(examples, models, label="full default dataset")
    save_path = "token_estimator.joblib"
    save_models(models, save_path)
    print(f"Saved trained models to {save_path}")

    demo_text = "Hello from the default workflow!"
    true_count = anthropic_count_tokens(demo_text, DEFAULT_MODEL)
    preds = predict_tokens(models, demo_text)
    print("\nDemo prediction on text:", repr(demo_text))
    print(f"  true    : {true_count}")
    for name, value in preds.items():
        print(f"  {name:8s}: {value}")


# -----------------------------
# CLI
# -----------------------------


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Anthropic token count estimator builder")

    subparsers = parser.add_subparsers(dest="command", required=True)

    # collect
    p_collect = subparsers.add_parser("collect", help="Collect (text, token_count) data from Anthropic")
    p_collect.add_argument("--model", required=True, help="Anthropic model name for count_tokens")
    p_collect.add_argument("--input", required=True, help="Input text file (one text per line)")
    p_collect.add_argument("--output", required=True, help="Output JSONL dataset path")

    # train
    p_train = subparsers.add_parser("train", help="Train estimators from a JSONL dataset")
    p_train.add_argument("--dataset", required=True, help="JSONL dataset created by 'collect'")
    p_train.add_argument("--model-out", required=True, help="Output path for trained estimator (joblib)")

    # estimate
    p_est = subparsers.add_parser("estimate", help="Estimate tokens for a text using trained models")
    p_est.add_argument("--model-in", required=True, help="Path to trained estimator (joblib)")

    text_group = p_est.add_mutually_exclusive_group(required=True)
    text_group.add_argument("--text", help="Text to estimate tokens for")
    text_group.add_argument("--text-file", help="File whose entire contents are used as the text")

    return parser.parse_args(argv)


def main(argv: List[str] | None = None) -> None:
    if argv is None:
        argv = sys.argv[1:]

    if not argv:
        print("Running default token-estimator workflow (no arguments provided)...")
        run_default_workflow()
        return

    args = parse_args(argv)

    if args.command == "collect":
        collect_dataset(model=args.model, input_path=args.input, output_path=args.output)

    elif args.command == "train":
        examples = read_jsonl(args.dataset)
        print(f"Loaded {len(examples)} examples from {args.dataset}")

        train_examples, test_examples = split_examples(examples, test_fraction=0.2, seed=42)
        print(f"Train examples: {len(train_examples)}, Test examples: {len(test_examples)}")

        models = train_models(train_examples)
        save_models(models, args.model_out)
        print(f"Saved trained models to {args.model_out}")

        evaluate_baseline_vs_models(train_examples, models, label="train set")
        if test_examples:
            evaluate_baseline_vs_models(test_examples, models, label="test set")

    elif args.command == "estimate":
        models = load_models(args.model_in)

        if args.text is not None:
            text = args.text
        else:
            with open(args.text_file, "r", encoding="utf-8") as f:
                text = f.read()

        preds = predict_tokens(models, text)

        print("Text preview:", repr(text[:120]))
        print("\nToken estimates:")
        for name, value in preds.items():
            print(f"  {name:8s}: {value}")

    else:
        raise RuntimeError(f"Unknown command: {args.command}")


if __name__ == "__main__":  # pragma: no cover
    main()