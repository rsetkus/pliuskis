#!/usr/bin/env python3
"""
Render a human-readable rollup view of a feature's progress from the
authoritative YAML files (epic YAMLs + story YAMLs + verification
artifacts on disk). This script is a READ-ONLY view -- it never mutates
state.

Every feature has at least one epic (PO writes at minimum `EPIC-1.yaml`).
The rollup always groups stories under their parent epic.

Two output modes:
  --print     write the rollup to stdout (default)
  --write     write the rollup to <output_dir>/<feature>/progress.md

Status badges shown for stories:
    [TODO] [CREATE_TESTS] [IN_DEV] [TESTING] [DONE] [BLOCKED]

Status badges shown for epics (derived from on-disk status + rollup):
    [TODO] [IN_PROGRESS  N/M] [DONE  N/M]
    [VERIFIED  N/M]                          // when epic YAML status == VERIFIED
    [BLOCKED  reason]

This file is intended to be regenerated whenever the user (or the
orchestrator) wants a snapshot. It is NOT a source of truth; the YAMLs
are.

CLI:
    python _tools/rollup_status.py --feature add_dark_mode
    python _tools/rollup_status.py --feature add_dark_mode --write

Exit codes:
    0  success
    1  error (feature dir missing, epics dir missing, YAML parse failure, etc.)
"""

import argparse
import re
import sys
from pathlib import Path

import yaml


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


def _story_sort_key(s):
    m = re.match(r"^STORY-(\d+)$", str(s))
    if m:
        return (0, int(m.group(1)))
    return (1, str(s))


def _epic_sort_key(s):
    m = re.match(r"^EPIC-(\d+)$", str(s))
    if m:
        return (0, int(m.group(1)))
    return (1, str(s))


def load_yaml_file(path):
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception as e:
        raise RuntimeError(f"YAML parse failed for {path}: {e}")
    if not isinstance(data, dict):
        raise RuntimeError(f"{path} did not contain a YAML mapping")
    return data


def load_all_stories(stories_dir):
    stories = {}
    for yaml_path in stories_dir.glob("STORY-*.yaml"):
        data = load_yaml_file(yaml_path)
        sid = data.get("id")
        if not sid:
            raise RuntimeError(f"{yaml_path} has no 'id' field")
        stories[sid] = data
    return stories


def load_all_epics(epics_dir):
    epics = {}
    for yaml_path in epics_dir.glob("EPIC-*.yaml"):
        data = load_yaml_file(yaml_path)
        eid = data.get("id")
        if not eid:
            raise RuntimeError(f"{yaml_path} has no 'id' field")
        epics[eid] = data
    return epics


def compute_epic_rollup(epic, stories):
    sids = epic.get("story_ids") or []
    if not sids:
        return "TODO", 0, 0
    statuses = [stories[sid].get("status") if sid in stories else None for sid in sids]
    done_count = sum(1 for st in statuses if st == "DONE")
    total = len(sids)
    if any(st == "BLOCKED" for st in statuses):
        return "BLOCKED", done_count, total
    if all(st == "DONE" for st in statuses):
        return "DONE", done_count, total
    if all(st == "TODO" for st in statuses):
        return "TODO", done_count, total
    return "IN_PROGRESS", done_count, total


def format_story_line(sid, story):
    status = story.get("status", "?")
    title = story.get("title", "")
    deps = story.get("dependencies") or []
    dep_str = f"  (deps: {', '.join(deps)})" if deps else ""
    blocked_reason = story.get("blocked_reason")
    reason_str = f"  -- {blocked_reason}" if status == "BLOCKED" and blocked_reason else ""
    return f"  - [{status:<12}] {sid:<10} {title}{dep_str}{reason_str}"


def format_epic_header(eid, epic, rollup, done, total, verification_exists):
    title = epic.get("title", "")
    on_disk = epic.get("status", "TODO")
    badge_status = on_disk if on_disk == "VERIFIED" else rollup
    if badge_status == "BLOCKED":
        reason = epic.get("blocked_reason") or ""
        badge = f"BLOCKED  {reason}" if reason else "BLOCKED"
    elif badge_status == "VERIFIED":
        badge = f"VERIFIED  {done}/{total}"
    elif badge_status == "DONE":
        suffix = "" if verification_exists else "  (ready to verify)"
        badge = f"DONE  {done}/{total}{suffix}"
    elif badge_status == "IN_PROGRESS":
        badge = f"IN_PROGRESS  {done}/{total}"
    else:
        badge = f"TODO  {done}/{total}"
    deps = epic.get("depends_on") or []
    dep_str = f"  (depends_on: {', '.join(deps)})" if deps else ""
    return f"## {eid}: {title}   [{badge}]{dep_str}"


def render(feature, feature_dir):
    stories_dir = feature_dir / "stories"
    epics_dir = feature_dir / "epics"
    verif_dir = feature_dir / "verification"

    if not stories_dir.is_dir():
        raise RuntimeError(f"stories directory not found: {stories_dir}")
    if not epics_dir.is_dir():
        raise RuntimeError(f"epics directory not found: {epics_dir} (every feature has at least one epic)")

    stories = load_all_stories(stories_dir)
    epics = load_all_epics(epics_dir)
    if not epics:
        raise RuntimeError(f"no epics found in {epics_dir} (every feature has at least one epic)")

    lines = []
    lines.append(f"# Feature: {feature}")
    lines.append("")

    if not stories:
        lines.append("_No stories found._")
        return "\n".join(lines) + "\n"

    ordered_epics = sorted(epics.keys(), key=_epic_sort_key)
    story_to_epic = {}
    for eid in ordered_epics:
        for sid in (epics[eid].get("story_ids") or []):
            story_to_epic[sid] = eid

    total_stories = len(stories)
    done_stories = sum(1 for s in stories.values() if s.get("status") == "DONE")
    verified_epics = sum(1 for e in epics.values() if e.get("status") == "VERIFIED")
    lines.append(f"_Stories: {done_stories}/{total_stories} DONE  |  Epics: {verified_epics}/{len(epics)} VERIFIED_")
    lines.append("")

    for eid in ordered_epics:
        epic = epics[eid]
        rollup, done, total = compute_epic_rollup(epic, stories)
        verification_exists = (verif_dir / f"{eid}.md").exists()
        lines.append(format_epic_header(eid, epic, rollup, done, total, verification_exists))
        for sid in sorted(epic.get("story_ids") or [], key=_story_sort_key):
            if sid in stories:
                lines.append(format_story_line(sid, stories[sid]))
            else:
                lines.append(f"  - [MISSING     ] {sid:<10} <story file not found>")
        lines.append("")

    orphans = [sid for sid in stories.keys() if sid not in story_to_epic]
    if orphans:
        lines.append("## (orphan stories -- not in any epic; configuration bug)")
        for sid in sorted(orphans, key=_story_sort_key):
            lines.append(format_story_line(sid, stories[sid]))
        lines.append("")

    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="Render a human-readable rollup of feature progress from authoritative YAMLs.")
    parser.add_argument("--feature", required=True, help="Feature name (snake_case).")
    parser.add_argument("--write", action="store_true", help="Write to <output_dir>/<feature>/progress.md instead of stdout.")
    args = parser.parse_args()

    config = load_config()
    output_dir = get_output_dir(config)
    feature_dir = output_dir / args.feature

    if not feature_dir.is_dir():
        print(f"error: feature directory not found: {feature_dir}", file=sys.stderr)
        sys.exit(1)

    try:
        rendered = render(args.feature, feature_dir)
    except Exception as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(1)

    if args.write:
        out_path = feature_dir / "progress.md"
        out_path.write_text(rendered, encoding="utf-8", newline="\n")
        print(f"wrote {out_path}")
    else:
        sys.stdout.write(rendered)


if __name__ == "__main__":
    main()
