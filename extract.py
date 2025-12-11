

"""
extract.py

Utility script to generate a `prompts.txt` file from a directory full of
JSONL log files (like the one you uploaded).

Each JSONL file is expected to contain one JSON object per line. This script
looks for records that:
  - have type == "user"
  - have a "message" field that is a dict with a "role" == "user"
  - are not marked isMeta == True

For each such record, it takes message["content"], flattens any internal
newlines into spaces, and writes one prompt per line into prompts.txt.

Usage:

    # Basic: scan current directory recursively and write prompts.txt
    uv run extract.py

    # Specify input directory and output file explicitly
    uv run extract.py --input-dir path/to/jsonl_logs --output prompts.txt

Notes / caveats:

- This is tailored to the structure of the JSONL logs we inspected:
    { "type": "user", "message": {"role": "user", "content": "..."} }
  If your schema changes, you may need to tweak the extraction logic.

- Multi-line message content is flattened to a single line by replacing
  newlines with spaces. If you need *exact* reproduction of multi-line
  text, consider changing `tokens.py` to read JSONL directly instead of
  one-line-per-prompt text files.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Iterable, List


def iter_jsonl_files(root: Path) -> Iterable[Path]:
    """Yield all .jsonl files under root (recursively)."""
    if not root.is_dir():
        raise ValueError(f"Input path {root} is not a directory")
    yield from root.rglob("*.jsonl")


def extract_prompt_from_record(record: dict) -> str | None:
    """
    Given a JSON object from a log line, try to extract a user prompt string.

    Heuristics based on the inspected schema:
      - Only consider records with type == "user".
      - Only consider records with message.role == "user".
      - Skip records with isMeta == True.
      - Skip obvious internal command / stdout noise.

    Returns:
      A cleaned, single-line prompt string, or None if this record
      should not produce a prompt.
    """
    if record.get("type") != "user":
        return None

    if record.get("isMeta") is True:
        # Skip meta messages like the caveat / system wrapper
        return None

    msg = record.get("message")
    if not isinstance(msg, dict):
        return None

    if msg.get("role") != "user":
        return None

    content = msg.get("content")
    if not isinstance(content, str):
        return None

    # Filter out obvious internal command / plumbing messages
    lowered = content.strip().lower()
    if "<command-name>" in lowered or "<local-command-stdout>" in lowered:
        return None

    # Flatten to a single line for prompts.txt
    # This keeps tokens.py's "one text per line" contract.
    flattened = " ".join(content.splitlines()).strip()
    if not flattened:
        return None

    return flattened


def collect_prompts_from_dir(input_dir: Path) -> List[str]:
    """Scan all JSONL files under input_dir and collect prompts."""
    prompts: List[str] = []

    for jsonl_path in iter_jsonl_files(input_dir):
        try:
            with jsonl_path.open("r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        obj = json.loads(line)
                    except json.JSONDecodeError:
                        # Skip malformed lines
                        continue

                    prompt = extract_prompt_from_record(obj)
                    if prompt is not None:
                        prompts.append(prompt)
        except OSError as e:
            print(f"Warning: could not read {jsonl_path}: {e}")

    return prompts


def write_prompts(prompts: List[str], output_path: Path) -> None:
    """Write one prompt per line to output_path."""
    with output_path.open("w", encoding="utf-8") as f:
        for prompt in prompts:
            # Ensure no embedded newlines; flattening already did this,
            # but we guard just in case.
            line = " ".join(prompt.splitlines()).strip()
            if not line:
                continue
            f.write(line + "\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate prompts.txt from a directory of JSONL logs."
    )
    parser.add_argument(
        "--input-dir",
        type=str,
        default=".",
        help="Directory to scan for .jsonl files (default: current directory)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="prompts.txt",
        help="Output text file (default: prompts.txt)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_dir = Path(args.input_dir).resolve()
    output_path = Path(args.output).resolve()

    print(f"Scanning for .jsonl files under: {input_dir}")
    prompts = collect_prompts_from_dir(input_dir)
    print(f"Collected {len(prompts)} prompts.")
    print(f"Writing to: {output_path}")
    write_prompts(prompts, output_path)
    print("Done.")

if __name__ == "__main__":
    main()