#!/usr/bin/env python3
"""
List which stories are eligible for which role, based on story status,
dependency satisfaction, and epic-level dependency satisfaction.
Mechanical replacement for the scheduler's ad-hoc eligibility logic.

Every feature has at least one epic (PO writes at minimum `EPIC-1.yaml`).
Every story belongs to exactly one epic via its `epic:` field. This
script:
  - Computes each epic's rollup status (TODO/IN_PROGRESS/DONE/VERIFIED/BLOCKED)
    from its constituent stories + the on-disk epic YAML (VERIFIED is only
    written by EpicVerifier; it never auto-rolls).
  - Adds an `epic_dep_unmet` reason to any story whose parent epic has any
    `depends_on:` epic NOT at VERIFIED. Those stories stay in
    `blocked_on_deps` until their epic prerequisites verify.
  - Surfaces `epic_ready_to_verify`: epics whose rollup is DONE (every
    story DONE) but on-disk status is not yet VERIFIED -- these are ready
    for an EpicVerifier worker.

A story's `dependencies:` list is satisfied ONLY when every dep resolves
to a story file with `status: DONE`. Any other status (`TESTING`,
`IN_DEV`, `BLOCKED`, `TODO`, `CREATE_TESTS`) does NOT count -- including
TESTING, which can still re-cycle back to IN_DEV.

An epic's `depends_on:` is satisfied ONLY when every dep resolves to an
epic file with `status: VERIFIED`. `DONE` is not close enough -- the
verifier is the gate.

CLI:
    python _tools/list_eligible.py --feature add_dark_mode

Output (JSON on stdout):
    {
      "success": true,
      "feature": "add_dark_mode",
      "stories_dir": "_output/add_dark_mode/stories",
      "epics_dir": "_output/add_dark_mode/epics",
      "TestCreator":     ["STORY-2"],
      "Developer":       ["STORY-4", "STORY-5"],
      "Tester":          ["STORY-1"],
      "in_progress":     ["STORY-7"],
      "blocked_on_deps": {"STORY-3": ["STORY-1 (TESTING, needs DONE)"]},
      "blocked":         ["STORY-6"],
      "done":            [],
      "all_statuses":    {"STORY-1": "TESTING", ...},
      "epics": {
        "EPIC-1": {
          "status": "VERIFIED",
          "rollup": "DONE",                              // computed from stories; differs from `status` only when VERIFIED is written on disk
          "story_ids": ["STORY-1","STORY-2"],
          "depends_on": [],
          "depends_on_unmet": []                          // epic ids not at VERIFIED
        },
        "EPIC-2": {"status": "IN_PROGRESS", ...}
      },
      "epic_ready_to_verify": ["EPIC-1"]                  // epics with rollup DONE but on-disk status != VERIFIED
    }

Exit codes:
    0  success (even if no stories are eligible -- that's a valid scheduler state)
    1  error (feature dir missing, epics dir missing, YAML parse failure, etc.)
"""

import argparse
import json
import re
import sys
from pathlib import Path

import yaml


SAGE_ROLE_FOR_STATUS = {
    "TODO":         "TestCreator",
    "IN_DEV":       "Developer",
    "TESTING":      "Tester",
    "CREATE_TESTS": None,
    "DONE":         None,
    "BLOCKED":      None,
}


VALID_EPIC_STATUSES = {"TODO", "IN_PROGRESS", "DONE", "VERIFIED", "BLOCKED"}


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


def load_all_stories(stories_dir):
    stories = {}
    for yaml_path in stories_dir.glob("STORY-*.yaml"):
        try:
            data = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
        except Exception as e:
            raise RuntimeError(f"YAML parse failed for {yaml_path}: {e}")
        if not isinstance(data, dict):
            raise RuntimeError(f"{yaml_path} did not contain a YAML mapping")
        sid = data.get("id")
        if not sid:
            raise RuntimeError(f"{yaml_path} has no 'id' field")
        if sid != yaml_path.stem:
            print(f"[warn] filename {yaml_path.name} doesn't match id {sid!r}", file=sys.stderr)
        stories[sid] = data
    return stories


def load_all_epics(epics_dir):
    epics = {}
    for yaml_path in epics_dir.glob("EPIC-*.yaml"):
        try:
            data = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
        except Exception as e:
            raise RuntimeError(f"YAML parse failed for {yaml_path}: {e}")
        if not isinstance(data, dict):
            raise RuntimeError(f"{yaml_path} did not contain a YAML mapping")
        eid = data.get("id")
        if not eid:
            raise RuntimeError(f"{yaml_path} has no 'id' field")
        epics[eid] = data
    return epics


def deps_status(story, all_statuses):
    unmet = []
    deps = story.get("dependencies") or []
    if not isinstance(deps, list):
        return [f"<malformed dependencies field: {deps!r}>"]
    for dep_id in deps:
        if dep_id not in all_statuses:
            unmet.append(f"{dep_id} (not found)")
        elif all_statuses[dep_id] != "DONE":
            unmet.append(f"{dep_id} ({all_statuses[dep_id]}, needs DONE)")
    return unmet


def compute_epic_rollup(epic, stories):
    """Compute a rollup status for an epic from its constituent stories.

    Rules (computed; VERIFIED is NEVER auto-computed -- it must be written
    on the epic YAML by EpicVerifier):
      - BLOCKED   if any story is BLOCKED
      - DONE      if every story is DONE
      - TODO      if every story is TODO
      - IN_PROGRESS otherwise
    The returned value is the rollup-from-stories. The caller compares it
    to the on-disk status to decide whether the epic is ready to verify.
    """
    sids = epic.get("story_ids") or []
    if not isinstance(sids, list) or not sids:
        return "TODO"

    statuses = []
    for sid in sids:
        s = stories.get(sid)
        if s is None:
            return "BLOCKED"   # missing story is a configuration bug -- surface it
        statuses.append(s.get("status"))

    if any(st == "BLOCKED" for st in statuses):
        return "BLOCKED"
    if all(st == "DONE" for st in statuses):
        return "DONE"
    if all(st == "TODO" for st in statuses):
        return "TODO"
    return "IN_PROGRESS"


def epic_dep_unmet(epic, epics):
    """Return list of epic ids in epic.depends_on that are NOT yet VERIFIED."""
    unmet = []
    deps = epic.get("depends_on") or []
    if not isinstance(deps, list):
        return [f"<malformed depends_on field: {deps!r}>"]
    for dep_id in deps:
        if dep_id not in epics:
            unmet.append(f"{dep_id} (not found)")
        elif epics[dep_id].get("status") != "VERIFIED":
            unmet.append(f"{dep_id} ({epics[dep_id].get('status')}, needs VERIFIED)")
    return unmet


def classify(feature, stories_dir, epics_dir):
    stories = load_all_stories(stories_dir)
    epics = load_all_epics(epics_dir)
    if not epics:
        raise RuntimeError(
            f"no epics found in {epics_dir}. Every feature has at least one epic "
            f"(see agents/product-owner.md § Epics)."
        )

    all_statuses = {sid: s.get("status") for sid, s in stories.items()}

    result = {
        "success": True,
        "feature": feature,
        "stories_dir": str(stories_dir),
        "epics_dir": str(epics_dir),
        "TestCreator": [],
        "Developer": [],
        "Tester": [],
        "in_progress": [],
        "blocked_on_deps": {},
        "blocked": [],
        "done": [],
        "all_statuses": all_statuses,
        "epics": {},
        "epic_ready_to_verify": [],
    }

    epic_unmet_cache = {}   # epic_id -> list of unmet epic deps
    story_to_epic = {}      # sid -> epic_id

    for eid in sorted(epics.keys(), key=_epic_sort_key):
        epic = epics[eid]
        rollup = compute_epic_rollup(epic, stories)
        on_disk = epic.get("status", "TODO")
        unmet = epic_dep_unmet(epic, epics)
        epic_unmet_cache[eid] = unmet
        for sid in (epic.get("story_ids") or []):
            story_to_epic[sid] = eid

        result["epics"][eid] = {
            "status": on_disk,
            "rollup": rollup,
            "story_ids": list(epic.get("story_ids") or []),
            "depends_on": list(epic.get("depends_on") or []),
            "depends_on_unmet": unmet,
        }

        # Ready-to-verify: every story DONE but on-disk status is not yet VERIFIED.
        if rollup == "DONE" and on_disk != "VERIFIED":
            result["epic_ready_to_verify"].append(eid)

    # Classify each story.
    for sid in sorted(stories.keys(), key=_story_sort_key):
        story = stories[sid]
        status = story.get("status")

        if status == "DONE":
            result["done"].append(sid)
            continue
        if status == "BLOCKED":
            result["blocked"].append(sid)
            continue
        if status == "CREATE_TESTS":
            result["in_progress"].append(sid)
            continue

        role = SAGE_ROLE_FOR_STATUS.get(status)
        if role is None:
            result["blocked_on_deps"][sid] = [f"<unexpected status: {status!r}>"]
            continue

        unmet = deps_status(story, all_statuses)

        eid = story_to_epic.get(sid) or story.get("epic")
        if eid is None:
            unmet.append("<story has no epic field>")
        elif eid not in epics:
            unmet.append(f"<story epic {eid!r} not found in epics dir>")
        else:
            for dep_unmet in epic_unmet_cache.get(eid, []):
                unmet.append(f"epic_dep:{eid} -> {dep_unmet}")

        if unmet:
            result["blocked_on_deps"][sid] = unmet
            continue

        result[role].append(sid)

    return result


def main():
    parser = argparse.ArgumentParser(description="List which stories are eligible for which role; surface epics ready to verify.")
    parser.add_argument("--feature", required=True, help="Feature name (snake_case). Reads from <output_dir>/<feature>/.")
    args = parser.parse_args()

    config = load_config()
    output_dir = get_output_dir(config)
    feature_dir = output_dir / args.feature
    stories_dir = feature_dir / "stories"
    epics_dir = feature_dir / "epics"

    if not stories_dir.is_dir():
        print(json.dumps({
            "success": False,
            "error": f"stories directory not found: {stories_dir}",
            "feature": args.feature,
        }))
        sys.exit(1)

    if not epics_dir.is_dir():
        print(json.dumps({
            "success": False,
            "error": f"epics directory not found: {epics_dir} (every feature has at least one epic)",
            "feature": args.feature,
        }))
        sys.exit(1)

    try:
        result = classify(args.feature, stories_dir, epics_dir)
    except Exception as e:
        print(json.dumps({
            "success": False,
            "error": str(e),
            "error_type": type(e).__name__,
            "feature": args.feature,
            "stories_dir": str(stories_dir),
            "epics_dir": str(epics_dir),
        }))
        sys.exit(1)

    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
