#!/usr/bin/env python3
"""
Record one worker's token usage and re-render the feature's TOKENS summary.

Two modes:
  TEAM mode (--transcript <jsonl>):
      Exact tokens from a spawned agent's JSONL transcript (parsed via
      extract_token_usage.py).

  INLINE mode (--inline --output-chars <n>):
      Estimated tokens for an inline-skill invocation (no agent spawn,
      no transcript). Estimate is rough -- inline entries are marked
      `estimated: true` in the JSON store.

Each call:
  1. Computes a worker entry (role, story, cycle, tokens, cost)
  2. Appends it to <output_dir>/FEATURE_<feature>_TOKENS.json
  3. Re-renders <output_dir>/FEATURE_<feature>_TOKENS.md from the JSON

Pricing comes from sage-config.yaml -> pricing block. Built-in defaults ship
for current Claude models; override only if you've negotiated rates or use a
model the defaults don't cover.
"""

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import yaml


DEFAULT_PRICING = {
    "claude-opus-4-7": {
        "input": 15.00, "output": 75.00,
        "cache_read": 1.50, "cache_create": 18.75,
    },
    "claude-sonnet-4-6": {
        "input": 3.00, "output": 15.00,
        "cache_read": 0.30, "cache_create": 3.75,
    },
    "claude-haiku-4-5": {
        "input": 1.00, "output": 5.00,
        "cache_read": 0.10, "cache_create": 1.25,
    },
}


def find_sage_config():
    cur = Path.cwd()
    for _ in range(10):
        c = cur / "sage-config.yaml"
        if c.exists():
            return c
        if cur.parent == cur:
            break
        cur = cur.parent
    return None


def load_config():
    p = find_sage_config()
    if p is None:
        return {}
    with p.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def get_output_dir(config):
    return Path((config.get("paths") or {}).get("output_dir", "_output"))


def get_pricing(config, model):
    pricing = (config.get("pricing") or {})
    if model in pricing:
        return pricing[model]
    if model in DEFAULT_PRICING:
        return DEFAULT_PRICING[model]
    base = re.sub(r"-\d{8}$", "", model or "")
    if base in pricing:
        return pricing[base]
    if base in DEFAULT_PRICING:
        return DEFAULT_PRICING[base]
    return None


def compute_cost(tokens, rates):
    if rates is None:
        return None
    cost = 0.0
    for k in ("input", "output", "cache_read", "cache_create"):
        n = tokens.get(k, 0) or 0
        rate = rates.get(k, 0) or 0
        cost += (n / 1_000_000) * rate
    return cost


def parse_transcript(transcript_path):
    extractor = Path(__file__).parent / "extract_token_usage.py"
    result = subprocess.run(
        [sys.executable, str(extractor), str(transcript_path)],
        capture_output=True, text=True, timeout=30,
    )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        return {"success": False, "error": f"extractor output not JSON: {result.stdout[:200]}"}


def build_team_entry(args, config):
    parsed = parse_transcript(args.transcript)
    if not parsed.get("success"):
        return None, parsed.get("error", "transcript parse failed")

    tokens = parsed["tokens"]
    model = parsed.get("model")
    rates = get_pricing(config, model)
    cost = compute_cost(tokens, rates)

    return {
        "id": f"{args.role}-{args.story}-c{args.cycle}",
        "role": args.role,
        "story": args.story,
        "cycle": args.cycle,
        "mode": "team",
        "model": model,
        "tokens": tokens,
        "cost_usd": cost,
        "transcript": parsed["transcript"],
        "message_count": parsed.get("message_count"),
        "estimated": False,
        "recorded_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }, None


def estimate_inline_tokens(output_chars):
    output_chars = max(0, int(output_chars or 0))
    return {
        "input": output_chars // 2,
        "output": output_chars // 4,
        "cache_read": 0,
        "cache_create": 0,
    }


def build_inline_entry(args, config):
    tokens = estimate_inline_tokens(args.output_chars)
    model = ((config.get("pricing") or {}).get("default_model")
             or "claude-opus-4-7")
    rates = get_pricing(config, model)
    cost = compute_cost(tokens, rates)

    return {
        "id": f"{args.role}-{args.story}-c{args.cycle}-inline",
        "role": args.role,
        "story": args.story,
        "cycle": args.cycle,
        "mode": "inline",
        "model": model,
        "tokens": tokens,
        "cost_usd": cost,
        "estimated": True,
        "recorded_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }, None


def load_store(json_path, feature):
    if json_path.exists():
        with json_path.open("r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "feature": feature,
        "started_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "last_updated": None,
        "workers": [],
    }


def save_store(json_path, store):
    store["last_updated"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    json_path.parent.mkdir(parents=True, exist_ok=True)
    with json_path.open("w", encoding="utf-8") as f:
        json.dump(store, f, indent=2)


def fmt_int(n):
    if n is None:
        return "-"
    return f"{int(n):,}"


def fmt_cost(c):
    if c is None:
        return "-"
    if c < 0.01:
        return f"${c:.4f}"
    return f"${c:.2f}"


def aggregate(entries, key):
    out = {}
    for e in entries:
        k = e.get(key, "?")
        bucket = out.setdefault(k, {
            "count": 0,
            "tokens": {"input": 0, "output": 0, "cache_read": 0, "cache_create": 0},
            "cost": 0.0,
        })
        bucket["count"] += 1
        for tk in ("input", "output", "cache_read", "cache_create"):
            bucket["tokens"][tk] += e["tokens"].get(tk, 0)
        if e.get("cost_usd") is not None:
            bucket["cost"] += e["cost_usd"]
    return out


def _story_sort_key(s):
    m = re.match(r"^STORY-(\d+)$", str(s))
    if m:
        return (0, int(m.group(1)))
    return (1, str(s))


def dedup_workers(workers):
    """Collapse multiple transcripts for the same logical worker.

    The JSON store is append-only -- every transcript captured at every
    scheduling scan goes in. But when Claude Code's `Agent(name=X)` is called
    repeatedly for the same worker name (which happens automatically as the
    team conversation evolves and worker messages flow), each invocation
    produces a new transcript file. Subsequent transcripts capture the FULL
    growing team conversation, so they cumulatively contain the same messages
    as earlier transcripts plus a bit more.

    Summing usage across all transcripts double-counts the same conversation
    turns. The correct approach: keep only the most-complete transcript per
    logical worker (identified by `id` field like 'Tester-STORY-3-c2').

    "Most complete" = largest message_count if present, else largest cache_read
    (proxy for total work done).
    """
    by_id = {}
    for w in workers:
        wid = w.get("id")
        if wid is None:
            # No id (shouldn't happen for sage workers) -- pass through unchanged
            wid = f"__no_id__{len(by_id)}"
            by_id[wid] = w
            continue
        existing = by_id.get(wid)
        if existing is None:
            by_id[wid] = w
            continue
        # Pick the most-complete one
        new_size = w.get("message_count") or sum(w["tokens"].values())
        old_size = existing.get("message_count") or sum(existing["tokens"].values())
        if new_size > old_size:
            by_id[wid] = w
    return list(by_id.values())


def render_markdown(store, config):
    feature = store["feature"]
    raw_workers = store["workers"]
    last_updated = store.get("last_updated", "?")

    if not raw_workers:
        return f"# Token Usage: {feature}\n\nNo workers recorded yet.\n"

    # DEDUP: same logical worker (e.g., 'Tester-STORY-3-c2') often has multiple
    # transcripts captured -- each subsequent spawn re-records the growing team
    # conversation. Sum across them would massively over-count. Use only the
    # most-complete transcript per logical id for all totals/breakdowns.
    workers = dedup_workers(raw_workers)
    dedup_removed = len(raw_workers) - len(workers)

    total_tokens = {"input": 0, "output": 0, "cache_read": 0, "cache_create": 0}
    total_cost = 0.0
    cost_unknown_count = 0
    estimated_count = 0
    for w in workers:
        for tk in total_tokens:
            total_tokens[tk] += w["tokens"].get(tk, 0)
        if w.get("cost_usd") is None:
            cost_unknown_count += 1
        else:
            total_cost += w["cost_usd"]
        if w.get("estimated"):
            estimated_count += 1
    grand_total = sum(total_tokens.values())

    team_count = sum(1 for w in workers if w.get("mode") == "team")
    inline_count = sum(1 for w in workers if w.get("mode") == "inline")

    by_role = aggregate(workers, "role")
    by_story = aggregate(workers, "story")

    input_pieces = total_tokens["input"] + total_tokens["cache_read"] + total_tokens["cache_create"]
    cache_hit_rate = (total_tokens["cache_read"] / input_pieces) if input_pieces > 0 else 0.0

    per_bucket_cost = {"input": 0.0, "output": 0.0, "cache_read": 0.0, "cache_create": 0.0}
    for w in workers:
        rates = get_pricing(config, w.get("model"))
        if rates is None:
            continue
        for tk in per_bucket_cost:
            n = w["tokens"].get(tk, 0)
            per_bucket_cost[tk] += (n / 1_000_000) * rates.get(tk, 0)

    lines = []
    lines.append(f"# Token Usage: {feature}")
    lines.append("")
    lines.append(f"Last updated: {last_updated}")
    lines.append(f"Logical workers: {len(workers)} (team: {team_count}, inline: {inline_count})")
    if dedup_removed:
        lines.append(f"  - JSON store has {len(raw_workers)} raw transcript captures; {dedup_removed} were redundant re-spawns of the same logical worker (deduplicated for totals below)")
    if estimated_count:
        lines.append(f"  - {estimated_count} entries are inline-mode estimates (not exact)")
    if cost_unknown_count:
        lines.append(f"  - {cost_unknown_count} entries have no pricing -- cost shown as '-'")
    lines.append("")

    lines.append("## Totals")
    lines.append("")
    lines.append("| Bucket | Tokens | Cost (USD) |")
    lines.append("|---|---:|---:|")
    for label, key in [
        ("Input (fresh)", "input"),
        ("Output", "output"),
        ("Cache read", "cache_read"),
        ("Cache create", "cache_create"),
    ]:
        lines.append(f"| {label} | {fmt_int(total_tokens[key])} | {fmt_cost(per_bucket_cost[key])} |")
    lines.append(f"| **Total** | **{fmt_int(grand_total)}** | **{fmt_cost(total_cost)}** |")
    lines.append("")

    lines.append("## By Role")
    lines.append("")
    lines.append("| Role | Workers | Input | Output | Cache R | Cache W | Total | Cost |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    for role in sorted(by_role.keys()):
        b = by_role[role]
        t = b["tokens"]
        total = sum(t.values())
        lines.append(
            f"| {role} | {b['count']} | {fmt_int(t['input'])} | {fmt_int(t['output'])} "
            f"| {fmt_int(t['cache_read'])} | {fmt_int(t['cache_create'])} "
            f"| {fmt_int(total)} | {fmt_cost(b['cost'])} |"
        )
    lines.append("")

    lines.append("## By Story")
    lines.append("")
    lines.append("| Story | Workers | Input | Output | Cache R | Cache W | Total | Cost |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    for story in sorted(by_story.keys(), key=_story_sort_key):
        b = by_story[story]
        t = b["tokens"]
        total = sum(t.values())
        lines.append(
            f"| {story} | {b['count']} | {fmt_int(t['input'])} | {fmt_int(t['output'])} "
            f"| {fmt_int(t['cache_read'])} | {fmt_int(t['cache_create'])} "
            f"| {fmt_int(total)} | {fmt_cost(b['cost'])} |"
        )
    lines.append("")

    lines.append("## Cache Effectiveness")
    lines.append("")
    lines.append(f"- **Cache hit rate:** {cache_hit_rate*100:.1f}% "
                 f"({fmt_int(total_tokens['cache_read'])} of {fmt_int(input_pieces)} input tokens served from cache)")
    savings = 0.0
    for w in workers:
        rates = get_pricing(config, w.get("model"))
        if rates is None:
            continue
        cr = w["tokens"].get("cache_read", 0)
        diff_per_token = (rates.get("input", 0) - rates.get("cache_read", 0)) / 1_000_000
        savings += cr * diff_per_token
    lines.append(f"- **Estimated savings vs no caching:** {fmt_cost(savings)}")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("Auto-generated by `_tools/record_worker_usage.py`. Do not hand-edit -- "
                 "edits will be lost on the next worker recording.")
    lines.append("")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Record one worker's token usage and re-render the feature TOKENS summary.")
    parser.add_argument("--feature", required=True, help="Feature name (snake_case)")
    parser.add_argument("--role", required=True, help="Worker role (ProductOwner | TestCreator | Developer | Tester)")
    parser.add_argument("--story", default="-", help="Story ID (e.g., STORY-3); '-' for non-story workers like ProductOwner")
    parser.add_argument("--cycle", type=int, default=1, help="Cycle number for this story (default 1)")
    grp = parser.add_mutually_exclusive_group(required=True)
    grp.add_argument("--transcript", help="Path to the agent's JSONL transcript (TEAM mode)")
    grp.add_argument("--inline", action="store_true", help="Inline mode (estimated tokens; pair with --output-chars)")
    parser.add_argument("--output-chars", type=int, default=0, help="Approximate output character count (INLINE mode)")
    args = parser.parse_args()

    config = load_config()

    if args.transcript:
        entry, err = build_team_entry(args, config)
    else:
        entry, err = build_inline_entry(args, config)

    if entry is None:
        print(json.dumps({"success": False, "error": err}))
        sys.exit(1)

    output_dir = get_output_dir(config)
    feature_dir = output_dir / args.feature
    json_path = feature_dir / "tokens.json"
    md_path = feature_dir / "tokens.md"

    store = load_store(json_path, args.feature)
    store["workers"].append(entry)
    save_store(json_path, store)

    md = render_markdown(store, config)
    md_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.write_text(md, encoding="utf-8", newline="\n")

    print(json.dumps({
        "success": True,
        "feature": args.feature,
        "worker_id": entry["id"],
        "tokens": entry["tokens"],
        "cost_usd": entry["cost_usd"],
        "json_path": str(json_path),
        "md_path": str(md_path),
        "total_workers": len(store["workers"]),
    }))


if __name__ == "__main__":
    main()
