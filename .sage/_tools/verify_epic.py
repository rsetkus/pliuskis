#!/usr/bin/env python3
"""
Verify an epic is ready for the VERIFIED gate.

Every feature has at least one epic, so the EpicVerifier always runs in
epic scope -- no whole-feature mode.

Preconditions and checks (in order):
  1. Every story in the epic's `story_ids:` is `status: DONE` (re-read
     from YAML).
  2. Every story has an AC implementation map sidecar that
     verify_ac_map.py still accepts (re-run the per-story verifier
     mechanically; a story may have regressed since it last passed if
     other stories edited shared files).
  3. Optional epic-level acceptance: if the epic YAML has an `acceptance:`
     block, surface it for the EpicVerifier agent (this script does not
     interpret prose -- the agent decides; this is just a checklist).

This script does NOT run tests. The EpicVerifier agent runs the full
regression for the epic (multi-story) using the project's test runner;
this script handles the mechanical preconditions only.

CLI:
    python _tools/verify_epic.py --feature add_dark_mode --epic EPIC-1

Output (JSON on stdout):
    {
      "success": true,
      "feature": "add_dark_mode",
      "scope": "EPIC-1",
      "stories_in_scope": ["STORY-1", "STORY-2"],
      "preconditions": {
        "all_done": true,
        "ac_maps_verified": true,
        "failed_stories": []
      },
      "epic_acceptance": "..."     // present when the epic YAML has `acceptance:`
    }

    {
      "success": false,
      "feature": "...",
      "scope": "EPIC-N",
      "preconditions": {
        "all_done": false,
        "non_done": {"STORY-3": "IN_DEV"},
        "ac_maps_verified": false,
        "failed_stories": [{"story": "STORY-2", "verifier_output": {...}}]
      }
    }

Exit codes:
    0  success (epic is verifiable: all stories DONE + AC maps still good)
    1  precondition failure (some story not DONE, or some AC map fails)
    2  invocation error (feature dir missing, bad args, YAML parse failure)
"""

import argparse
import json
import re
import subprocess
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


def load_all_stories(stories_dir):
    stories = {}
    for yaml_path in sorted(stories_dir.glob("STORY-*.yaml")):
        try:
            data = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
        except Exception as e:
            raise RuntimeError(f"YAML parse failed for {yaml_path}: {e}")
        if not isinstance(data, dict):
            raise RuntimeError(f"{yaml_path} did not contain a YAML mapping")
        sid = data.get("id")
        if not sid:
            raise RuntimeError(f"{yaml_path} has no 'id' field")
        stories[sid] = data
    return stories


def load_epic(epics_dir, epic_id):
    p = epics_dir / f"{epic_id}.yaml"
    if not p.exists():
        raise RuntimeError(f"epic file not found: {p}")
    data = yaml.safe_load(p.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise RuntimeError(f"{p} did not contain a YAML mapping")
    if data.get("id") != epic_id:
        raise RuntimeError(f"id mismatch in {p}: expected {epic_id}, found {data.get('id')!r}")
    return data


def run_verify_ac_map(story_id, stories_dir):
    """Invoke verify_ac_map.py for a single story; return parsed JSON output."""
    script = Path(__file__).parent / "verify_ac_map.py"
    proc = subprocess.run(
        [sys.executable, str(script), story_id, "--stories-dir", str(stories_dir)],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    out = (proc.stdout or "").strip()
    try:
        payload = json.loads(out) if out else {}
    except json.JSONDecodeError:
        payload = {"success": False, "error": f"non-JSON output: {out!r}", "stderr": proc.stderr}
    return payload


def verify(feature, scope_label, story_ids, stories_dir, epic_data=None):
    statuses = {}
    not_found = []
    all_stories = load_all_stories(stories_dir)
    for sid in story_ids:
        if sid not in all_stories:
            not_found.append(sid)
        else:
            statuses[sid] = all_stories[sid].get("status")

    non_done = {sid: st for sid, st in statuses.items() if st != "DONE"}
    all_done = (len(non_done) == 0 and not not_found)

    if not_found:
        return {
            "success": False,
            "feature": feature,
            "scope": scope_label,
            "stories_in_scope": list(story_ids),
            "preconditions": {
                "all_done": False,
                "non_done": non_done,
                "not_found": not_found,
                "ac_maps_verified": False,
                "failed_stories": [],
            },
        }

    # Even when not all done, we report AC map verification for the DONE stories so the agent
    # has full context, but the top-level success flag depends on both gates.
    failed_ac_maps = []
    for sid in sorted(story_ids, key=_story_sort_key):
        if statuses.get(sid) != "DONE":
            continue
        result = run_verify_ac_map(sid, stories_dir)
        if not result.get("success"):
            failed_ac_maps.append({"story": sid, "verifier_output": result})

    ac_maps_verified = (len(failed_ac_maps) == 0)
    success = all_done and ac_maps_verified

    out = {
        "success": success,
        "feature": feature,
        "scope": scope_label,
        "stories_in_scope": sorted(story_ids, key=_story_sort_key),
        "preconditions": {
            "all_done": all_done,
            "non_done": non_done,
            "ac_maps_verified": ac_maps_verified,
            "failed_stories": failed_ac_maps,
        },
    }

    if epic_data is not None:
        acceptance = epic_data.get("acceptance")
        if acceptance:
            out["epic_acceptance"] = acceptance

    return out


def main():
    parser = argparse.ArgumentParser(description="Verify an epic is ready for VERIFIED.")
    parser.add_argument("--feature", required=True, help="Feature name (snake_case).")
    parser.add_argument("--epic", required=True, help="Epic id (e.g., EPIC-1). Reads stories listed in that epic's story_ids.")
    args = parser.parse_args()

    config = load_config()
    output_dir = get_output_dir(config)
    feature_dir = output_dir / args.feature
    stories_dir = feature_dir / "stories"
    epics_dir = feature_dir / "epics"

    if not stories_dir.is_dir():
        print(json.dumps({"success": False, "error": f"stories directory not found: {stories_dir}"}))
        sys.exit(2)
    if not epics_dir.is_dir():
        print(json.dumps({"success": False, "error": f"epics directory not found: {epics_dir}"}))
        sys.exit(2)

    try:
        epic_data = load_epic(epics_dir, args.epic)
        story_ids = list(epic_data.get("story_ids") or [])
        if not story_ids:
            print(json.dumps({"success": False, "error": f"epic {args.epic} has no story_ids"}))
            sys.exit(2)
        result = verify(args.feature, args.epic, story_ids, stories_dir, epic_data=epic_data)
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e), "error_type": type(e).__name__}))
        sys.exit(2)

    print(json.dumps(result, indent=2))
    sys.exit(0 if result.get("success") else 1)


if __name__ == "__main__":
    main()
