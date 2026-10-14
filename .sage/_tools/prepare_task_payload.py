#!/usr/bin/env python3
"""
Render a task-payload markdown block for embedding into a worker's task
message, containing the spec.md + one or more story YAMLs + optional epic
YAML, verbatim.

Purpose: avoid the 3-5 `Read` tool calls a fresh worker would otherwise
make at task start. The orchestrator already has this content in scope
(list_eligible.py loaded the story YAMLs to decide eligibility); shipping
it to the worker as part of the initial task message eliminates the
Read-and-process turn pairs at the front of the worker's conversation,
which trims cache_create (and downstream cache_read) on every API call
for the rest of the worker's life.

What's IN the payload:
  - spec.md content (skipped if missing -- dev-test mode)
  - each requested story YAML verbatim
  - optional epic YAML verbatim (when --epic is passed)

What's NOT in the payload (deliberately):
  - Project instructions (already baked into the worker's rendered system
    prompt via PROJECT_INSTRUCTIONS substitution)
  - Test files (vary per cycle; Developer reads only the failing ones)
  - AC implementation map sidecars (separate concern -- they vary per
    cycle; workers Read them directly when needed)

CLI:
    python _tools/prepare_task_payload.py --feature add_dark_mode --stories STORY-1
    python _tools/prepare_task_payload.py --feature add_dark_mode --stories STORY-1,STORY-2
    python _tools/prepare_task_payload.py --feature add_dark_mode --stories STORY-1 --epic EPIC-1

Output (markdown on stdout, ready to embed into a SendMessage body):

    --- TASK PAYLOAD (pre-fetched by orchestrator; treat as source of truth -- do not re-Read these from disk) ---

    ## Spec (verbatim from _output/<feature>/spec.md)
    <spec body>

    ## Story: STORY-1 (verbatim from _output/<feature>/stories/STORY-1.yaml)
    ```yaml
    <story body>
    ```

    ## Epic: EPIC-1 (verbatim from _output/<feature>/epics/EPIC-1.yaml)
    ```yaml
    <epic body>
    ```

    --- END TASK PAYLOAD ---

Exit codes:
    0  success
    1  invocation or read error (missing feature, missing story file, etc.)
"""

import argparse
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


def render_payload(feature, story_ids, epic_id, output_dir):
    feature_dir = output_dir / feature
    spec_file = feature_dir / "spec.md"
    stories_dir = feature_dir / "stories"
    epics_dir = feature_dir / "epics"

    if not feature_dir.is_dir():
        raise RuntimeError(f"feature dir not found: {feature_dir}")

    parts = []
    parts.append("--- TASK PAYLOAD (pre-fetched by orchestrator; treat as source of truth -- do not re-Read these from disk) ---")
    parts.append("")

    # Spec (optional -- skipped in dev-test mode where there's no spec)
    if spec_file.exists():
        spec_body = spec_file.read_text(encoding="utf-8").rstrip()
        parts.append(f"## Spec (verbatim from {spec_file.as_posix()})")
        parts.append("")
        parts.append(spec_body)
        parts.append("")
    else:
        parts.append(f"## Spec")
        parts.append("")
        parts.append(f"_(no spec.md at {spec_file.as_posix()} -- dev-test mode, ignore)_")
        parts.append("")

    # Stories (required -- at least one)
    if not story_ids:
        raise ValueError("at least one story id is required")

    for sid in story_ids:
        story_file = stories_dir / f"{sid}.yaml"
        if not story_file.exists():
            raise RuntimeError(f"story file not found: {story_file}")
        story_body = story_file.read_text(encoding="utf-8").rstrip()
        parts.append(f"## Story: {sid} (verbatim from {story_file.as_posix()})")
        parts.append("")
        parts.append("```yaml")
        parts.append(story_body)
        parts.append("```")
        parts.append("")

    # Epic (optional -- included when caller passes --epic)
    if epic_id:
        epic_file = epics_dir / f"{epic_id}.yaml"
        if not epic_file.exists():
            raise RuntimeError(f"epic file not found: {epic_file}")
        epic_body = epic_file.read_text(encoding="utf-8").rstrip()
        parts.append(f"## Epic: {epic_id} (verbatim from {epic_file.as_posix()})")
        parts.append("")
        parts.append("```yaml")
        parts.append(epic_body)
        parts.append("```")
        parts.append("")

    parts.append("--- END TASK PAYLOAD ---")

    return "\n".join(parts) + "\n"


def parse_stories_arg(value):
    """Accept comma-separated story IDs (e.g., 'STORY-1,STORY-2')."""
    ids = [s.strip() for s in value.split(",") if s.strip()]
    if not ids:
        raise argparse.ArgumentTypeError("at least one story id required")
    return ids


def main():
    parser = argparse.ArgumentParser(description="Render a task payload (spec + story YAMLs + optional epic YAML) for embedding into a worker task message.")
    parser.add_argument("--feature", required=True, help="Feature name (snake_case).")
    parser.add_argument("--stories", required=True, type=parse_stories_arg,
                        help="Story IDs to include, comma-separated (e.g. STORY-1 or STORY-1,STORY-2).")
    parser.add_argument("--epic", default=None, help="Optional epic id (e.g. EPIC-1). Include the epic YAML when relevant (EpicVerifier task, or when story epic context matters).")
    args = parser.parse_args()

    config = load_config()
    output_dir = get_output_dir(config)

    try:
        rendered = render_payload(args.feature, args.stories, args.epic, output_dir)
    except Exception as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(1)

    sys.stdout.write(rendered)


if __name__ == "__main__":
    main()
