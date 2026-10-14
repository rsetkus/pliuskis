#!/usr/bin/env python3
"""
Parse a Claude Code agent's JSONL transcript and aggregate token usage.

Claude Code stores each spawned agent's full conversation as a JSONL file.
Each assistant turn carries a `usage` object with the same fields the
Anthropic API returns: input_tokens, output_tokens, cache_read_input_tokens,
cache_creation_input_tokens, plus the model name on the parent message.

This script walks the JSONL, sums usage across all assistant turns, and
returns a single JSON object on stdout.

CLI:
    python _tools/extract_token_usage.py /path/to/agent_transcript.jsonl

Output (JSON):
    {
      "success": true,
      "transcript": "/path/to/transcript.jsonl",
      "model": "claude-opus-4-7",
      "message_count": 7,
      "tokens": {
        "input": 1234, "output": 5678,
        "cache_read": 9012, "cache_create": 345
      }
    }

Defensive parser: searches every JSON object on every line for a 'usage'
field and reads recognized numeric subfields. Tolerates unknown fields and
lines that aren't JSON.
"""

import argparse
import json
import sys
from pathlib import Path


USAGE_FIELDS = {
    "input_tokens": "input",
    "output_tokens": "output",
    "cache_read_input_tokens": "cache_read",
    "cache_creation_input_tokens": "cache_create",
}


def find_usage(obj):
    if isinstance(obj, dict):
        if "usage" in obj and isinstance(obj["usage"], dict):
            return obj["usage"]
        for v in obj.values():
            found = find_usage(v)
            if found is not None:
                return found
    elif isinstance(obj, list):
        for item in obj:
            found = find_usage(item)
            if found is not None:
                return found
    return None


def find_model(obj):
    if isinstance(obj, dict):
        if "model" in obj and isinstance(obj["model"], str):
            return obj["model"]
        for v in obj.values():
            found = find_model(v)
            if found is not None:
                return found
    elif isinstance(obj, list):
        for item in obj:
            found = find_model(item)
            if found is not None:
                return found
    return None


def aggregate_transcript(jsonl_path):
    path = Path(jsonl_path)
    if not path.exists():
        return {
            "success": False,
            "transcript": str(path),
            "error": f"transcript file not found: {path}",
        }

    totals = {key: 0 for key in USAGE_FIELDS.values()}
    message_count = 0
    model = None
    parse_errors = 0
    line_count = 0

    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line_count += 1
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                parse_errors += 1
                continue

            usage = find_usage(obj)
            if usage is None:
                continue

            message_count += 1
            for sdk_name, our_name in USAGE_FIELDS.items():
                val = usage.get(sdk_name, 0)
                if isinstance(val, (int, float)):
                    totals[our_name] += int(val)

            if model is None:
                model = find_model(obj)

    if message_count == 0:
        return {
            "success": False,
            "transcript": str(path),
            "lines": line_count,
            "parse_errors": parse_errors,
            "error": "no usage records found in transcript",
        }

    return {
        "success": True,
        "transcript": str(path),
        "model": model,
        "message_count": message_count,
        "lines": line_count,
        "parse_errors": parse_errors,
        "tokens": totals,
    }


def main():
    parser = argparse.ArgumentParser(description="Aggregate token usage from a Claude Code agent JSONL transcript.")
    parser.add_argument("transcript", help="Path to the agent's JSONL transcript file")
    args = parser.parse_args()

    try:
        result = aggregate_transcript(args.transcript)
    except Exception as e:
        result = {
            "success": False,
            "transcript": args.transcript,
            "error": str(e),
            "error_type": type(e).__name__,
        }

    print(json.dumps(result))
    sys.exit(0 if result.get("success") else 1)


if __name__ == "__main__":
    main()
