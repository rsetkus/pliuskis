#!/usr/bin/env python3
"""
Token tracker: walks Claude Code's per-project subagent transcripts, records
any sage worker not yet in the feature's TOKENS.json store.

Idempotent: running it twice does not duplicate entries -- already-recorded
transcripts are detected by their .jsonl path (which is stored in each worker
entry as the `transcript` field).

Why this exists: the orchestrator's "record after each worker" instruction is
prompt-driven and easy to skip. This script is the safety net -- run it any
time to true-up the token store against what actually ran.

Subagent transcripts live at:
  ~/.claude/projects/<project-slug>/<session-id>/subagents/agent-<hash>.jsonl
  ~/.claude/projects/<project-slug>/<session-id>/subagents/agent-<hash>.meta.json

The .meta.json has shape: {"agentType": "<worker_name>"} where worker_name
matches the orchestrator's naming convention (e.g., "ProductOwner",
"TestCreator-STORY-3", "Developer-STORY-3-c2"). Discovery parses the
worker_name to extract role/story/cycle.

SESSION SCOPING (the default, matches /usage semantics):
By default this script scopes to the **current Claude Code session** -- it
auto-detects which `<project>/<session-uuid>/` directory has the most recent
activity and only scans that one. This avoids pulling in transcripts from
previous Claude Code sessions (separate interrupt-and-restart cycles), which
would inflate totals far beyond what the user actually ran in this session.

CLI:
    # Default: current session, sage workers only:
    python _tools/discover_and_record.py --feature add_dark_mode

    # Optionally tighten by time window (e.g., only the last 60 min within
    # the current session -- useful if multiple workflows ran in one session):
    python _tools/discover_and_record.py --feature add_dark_mode --since-minutes 60

    # Explicit session id (skip auto-detect):
    python _tools/discover_and_record.py --feature add_dark_mode \
        --session-id 6e794ed5-e2b9-46be-87d8-3a426d79ce06

    # Legacy mode: scan ALL session dirs (inflated; useful for one-time audits):
    python _tools/discover_and_record.py --feature add_dark_mode --all-sessions

    # Override the project slug (auto-derived from project.absolute_root_dir):
    python _tools/discover_and_record.py --feature add_dark_mode --project-slug C--Users-...

    # Dry-run (list what would be recorded, don't actually record):
    python _tools/discover_and_record.py --feature add_dark_mode --dry-run

    # Force re-record everything in scope, even if already in store:
    python _tools/discover_and_record.py --feature add_dark_mode --force
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

import yaml


# Sage worker roles. Discovery filters out everything else (Claude Code's
# built-in Explore/Plan/general-purpose agents, custom subagents, etc.).
SAGE_ROLES = {"ProductOwner", "TestCreator", "Developer", "Tester"}


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


def project_root_to_slug(abs_root):
    """Translate an absolute project path to Claude Code's slug format.

    Every path separator (`:`, `\\`, `/`) becomes `-`. This naturally produces
    `--` for the Windows drive-letter colon (`C:` -> `C-`, then `\\` -> `-`,
    so `C:\\Users` -> `C--Users`). POSIX leading slash `/Users/yourname` becomes
    `-Users-yourname`.

    Example: C:\\Users\\yourname\\StudioProjects\\Breadcrumbs
        ->   C--Users-yourname-StudioProjects-Breadcrumbs
    """
    s = str(abs_root)
    s = s.replace(":", "-")
    s = s.replace("/", "-").replace("\\", "-")
    return s


def get_output_dir(config):
    return Path((config.get("paths") or {}).get("output_dir", "_output"))


def get_project_dir(project_slug):
    home = Path(os.path.expanduser("~"))
    return home / ".claude" / "projects" / project_slug


def get_subagents_dirs(project_slug, session_id=None):
    """Yield session subagent directories.

    If `session_id` is given, yield only that single session's dir (matches
    `/usage` scope: just the current Claude Code session). If None, yield
    every session dir under the project (old behavior, used by --all-sessions).
    """
    base = get_project_dir(project_slug)
    if not base.exists():
        return
    if session_id is not None:
        sub = base / session_id / "subagents"
        if sub.is_dir():
            yield sub
        return
    for session_dir in base.iterdir():
        if not session_dir.is_dir():
            continue
        sub = session_dir / "subagents"
        if sub.is_dir():
            yield sub


def detect_current_session_id(project_slug):
    """Heuristic: pick the session dir with the most recent activity.

    Looks at each `<project>/<session-uuid>/` and finds the newest mtime
    across its contained files (including subagents). The session dir
    containing the most-recently-modified file is taken as the active one.

    Returns the session uuid string, or None if no session dirs exist.
    """
    base = get_project_dir(project_slug)
    if not base.exists():
        return None

    candidates = []
    for session_dir in base.iterdir():
        if not session_dir.is_dir():
            continue
        # Find the newest mtime anywhere under this session dir
        newest = 0.0
        for path in session_dir.rglob("*"):
            try:
                mt = path.stat().st_mtime
                if mt > newest:
                    newest = mt
            except OSError:
                continue
        if newest > 0:
            candidates.append((newest, session_dir.name))

    if not candidates:
        return None
    candidates.sort(reverse=True)  # newest first
    return candidates[0][1]


def parse_worker_name(name):
    """Parse 'Role[-STORY-N[-cN]]' into (role, story, cycle)."""
    if not name:
        return ("Unknown", "-", 1)
    cycle_m = re.search(r"-c(\d+)$", name)
    cycle = int(cycle_m.group(1)) if cycle_m else 1
    if cycle_m:
        name = name[: cycle_m.start()]
    story_m = re.search(r"-(STORY-\d+)$", name)
    if story_m:
        return (name[: story_m.start()], story_m.group(1), cycle)
    return (name, "-", cycle)


def is_sage_worker(worker_name):
    role, _story, _cycle = parse_worker_name(worker_name)
    return role in SAGE_ROLES


def already_recorded(store, transcript_path):
    target = str(transcript_path).replace("\\", "/").lower()
    for w in store.get("workers", []):
        existing = (w.get("transcript") or "").replace("\\", "/").lower()
        if existing == target:
            return True
    return False


def collect_candidates(project_slug, since_minutes=None, session_id=None):
    """Yield (transcript_path, worker_name, mtime) for sage worker transcripts."""
    cutoff = (time.time() - since_minutes * 60) if since_minutes else None
    for sub_dir in get_subagents_dirs(project_slug, session_id=session_id):
        for jsonl in sub_dir.glob("agent-*.jsonl"):
            meta_path = jsonl.with_suffix(".meta.json")
            if not meta_path.exists():
                continue
            try:
                meta = json.loads(meta_path.read_text(encoding="utf-8"))
            except Exception:
                continue
            worker_name = meta.get("agentType")
            if not worker_name:
                continue
            if not is_sage_worker(worker_name):
                continue
            mtime = jsonl.stat().st_mtime
            if cutoff is not None and mtime < cutoff:
                continue
            yield jsonl, worker_name, mtime


def main():
    parser = argparse.ArgumentParser(description="Discover and record sage worker token usage from Claude Code subagent transcripts.")
    parser.add_argument("--feature", required=True, help="Feature name (snake_case)")
    parser.add_argument("--project-slug", default=None, help="Override the auto-derived Claude Code project slug")
    parser.add_argument("--since-minutes", type=int, default=None, help="Only consider transcripts modified in the last N minutes")
    parser.add_argument("--session-id", default=None, help="Scope to a specific Claude Code session UUID (default: auto-detect current session)")
    parser.add_argument("--all-sessions", action="store_true", help="Scan transcripts from EVERY session dir for this project (legacy behavior; gives inflated totals across past runs). Useful for one-time audits/backfills.")
    parser.add_argument("--force", action="store_true", help="Re-record even if already in the store")
    parser.add_argument("--dry-run", action="store_true", help="List what would be recorded without writing")
    args = parser.parse_args()

    config = load_config()
    abs_root = (config.get("project") or {}).get("absolute_root_dir")
    if args.project_slug:
        project_slug = args.project_slug
    elif abs_root:
        project_slug = project_root_to_slug(abs_root)
    else:
        print(json.dumps({"success": False, "error": "no --project-slug given and sage-config.yaml has no project.absolute_root_dir"}))
        sys.exit(1)

    # Resolve session scope:
    #   --all-sessions overrides everything (legacy mode)
    #   --session-id <uuid> takes precedence (explicit)
    #   otherwise auto-detect the current session
    session_id = None
    if args.all_sessions:
        session_id = None
        session_scope = "all-sessions (legacy)"
    elif args.session_id:
        session_id = args.session_id
        session_scope = f"explicit session {session_id}"
    else:
        session_id = detect_current_session_id(project_slug)
        if session_id is None:
            print(json.dumps({
                "success": False,
                "error": f"could not auto-detect current Claude Code session for project {project_slug}. Pass --session-id <uuid> or --all-sessions explicitly.",
                "project_slug": project_slug,
            }))
            sys.exit(1)
        session_scope = f"auto-detected session {session_id}"

    output_dir = get_output_dir(config)
    feature_dir = output_dir / args.feature
    json_path = feature_dir / "tokens.json"

    if json_path.exists():
        store = json.loads(json_path.read_text(encoding="utf-8"))
    else:
        store = {"feature": args.feature, "workers": []}

    candidates = list(collect_candidates(project_slug, args.since_minutes, session_id=session_id))
    candidates.sort(key=lambda t: t[2])  # oldest first -> chronological store

    if not candidates:
        print(json.dumps({
            "success": True,
            "project_slug": project_slug,
            "session_scope": session_scope,
            "discovered": 0,
            "recorded": 0,
            "skipped_already_recorded": 0,
            "message": f"no sage worker transcripts found ({session_scope})",
        }))
        return

    recorder = Path(__file__).parent / "record_worker_usage.py"

    discovered = 0
    recorded = 0
    skipped = 0
    failed = []
    actions = []

    for jsonl, worker_name, _mtime in candidates:
        discovered += 1
        if not args.force and already_recorded(store, jsonl):
            skipped += 1
            continue

        role, story, cycle = parse_worker_name(worker_name)

        if args.dry_run:
            actions.append({
                "would_record": worker_name,
                "role": role, "story": story, "cycle": cycle,
                "transcript": str(jsonl),
            })
            continue

        cmd = [
            sys.executable, str(recorder),
            "--feature", args.feature,
            "--role", role,
            "--story", story,
            "--cycle", str(cycle),
            "--transcript", str(jsonl),
        ]
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            if result.returncode != 0:
                failed.append({"worker": worker_name, "transcript": str(jsonl), "error": result.stdout + result.stderr})
                continue
            recorded += 1
            actions.append({"recorded": worker_name, "role": role, "story": story, "cycle": cycle})
        except Exception as e:
            failed.append({"worker": worker_name, "transcript": str(jsonl), "error": str(e)})

    print(json.dumps({
        "success": True,
        "project_slug": project_slug,
        "session_scope": session_scope,
        "feature": args.feature,
        "discovered": discovered,
        "recorded": recorded,
        "skipped_already_recorded": skipped,
        "failed": len(failed),
        "actions": actions,
        "failures": failed,
    }, indent=2))

    if failed and not args.dry_run:
        sys.exit(1)


if __name__ == "__main__":
    main()
