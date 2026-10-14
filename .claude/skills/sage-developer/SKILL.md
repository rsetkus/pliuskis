---
name: sage-developer
description: Run the Developer agent inline to implement code for the next ready story (or a specific story)
when_to_use: When you want to implement code for a story whose tests already exist (status IN_DEV), without running the full team workflow
---

# Sage Developer Skill (inline)

This skill runs the Developer role solo: implement code for one story, write the AC implementation map sidecar, flip the story to TESTING, and report to the user as plain text.

> **Path note:** All `python .sage/_tools/...` commands below assume an installed project (a `.sage/` directory exists at the project root). If you're running this skill from the sage-feature-team source repo itself (no `.sage/` exists), substitute `_tools/...` instead.

---

## Step 1: Parse Input

Usage:
```
/sage-developer                    # Auto-pick: next story at IN_DEV
/sage-developer STORY-3            # Target a specific story
/sage-developer --feature add_dark_mode STORY-3
```

Compute:
- **explicit_story** -- first positional arg matching `STORY-\d+`, else null
- **feature_name** -- `--feature <name>` if given, else auto-detect (Step 3)

---

---

## Step 2: Load Rendered Developer Prompt (for project instructions and role contract)

```bash
python .sage/_tools/load_agents.py full
```

From the JSON, extract `agents.Developer`. The rendered prompt has two kinds of content -- use them differently:

**Use these sections** (mode-agnostic role contract -- they apply to you):
- `.sage/agents/_BASE.md` § Project-Specific Instructions -- the project's code conventions, file structure
- `.sage/agents/developer.md` § Your Job
- `.sage/agents/developer.md` § Test Handling
- `.sage/agents/developer.md` § AC Implementation Map (mandatory per story) -- format, rules, FORBIDDEN words
- `.sage/agents/developer.md` § Key Rules

**Ignore these sections** (team-mode workflow that does not apply when invoked as a skill):
- `_BASE.md` § STOP / SILENCE RULE / Starting Message / Workflow / Completion Outcomes / Progress File Updates / Key Rules (All Agents)
- `developer.md` § Developer Workflow (After Receiving Task) -- this skill defines its own workflow below
- `developer.md` § Completion Message Format -- this skill reports to the user as plain text instead

If `success` is false, surface the loader's `error` and stop.

---

## Step 3: Detect Current Feature

If `feature_name` was passed via `--feature`, use it directly. Otherwise:

1. List directories matching `<output_dir>/*/stories/` (output_dir from sage-config.yaml; default `_output`)
2. **Zero matches** -> tell the user: "No <feature>/stories/ directory found. Run /sage-po and /sage-test-creator first." Stop.
3. **Exactly one match** -> use it; extract `feature_name` from the directory name
4. **Multiple matches** -> show the list to the user and ask which feature to work on. Wait for their answer before continuing.

Compute:
- `stories_dir = <output_dir>/<feature_name>/stories/`
- `spec_file   = <output_dir>/<feature_name>/spec.md`

---

## Step 4: Determine Target Story

Read every YAML file in `stories_dir` and `spec_file` (for feature-level context).

**If `explicit_story` was given:**
- Find `<stories_dir>/<STORY-N>.yaml`. Error if missing.
- Validate it's at `status: IN_DEV`. If not:
  - If `TODO` or `CREATE_TESTS`: tell the user no tests exist yet -- suggest `/sage-test-creator <story>` first; offer to abort or proceed anyway.
  - If `TESTING` or `DONE`: tell the user the story is past Developer's scope; ask whether to re-implement, switch story, or abort.

**If no story was given (auto-pick):** call the eligibility script:
```bash
python .sage/_tools/list_eligible.py --feature <feature_name>
```
Take the first story from the `Developer` list (already sorted lowest STORY-N first). If the list is empty, show the user the bucketing so they see why nothing's eligible, then stop. Likely next steps: `/sage-test-creator` to advance a `TODO` story to `IN_DEV`, or `/sage-tester` to validate stories at `TESTING`.

Set `target_story` to the chosen story ID.

---

## Step 5: Find the Tests for the Target Story

You need to know which test functions belong to `target_story` so you can reason about what must pass.

1. Read the project's **story-ID tagging convention** from `.sage/sage-test-creator-config.yaml` (the convention TestCreator used).
2. Search the test directory(ies) named in the project instructions for tests tagged with `target_story`.
3. Read those tests to understand the behavior they require.

If the tagging convention is missing or you can't find tests for `target_story`, stop and ask the user.

---

## Step 6: Do the Work

1. **Read the spec** (`spec_file` from Step 3) for feature-level context (overview, edge cases, tech notes), and read `target_story`'s YAML for its `acceptance_criteria:` block -- the per-story contract you must satisfy.
2. **Implement the code** to satisfy **every AC** in `target_story` (not just the AC the tests cover) AND make `target_story`'s tests pass. Do NOT break tests for stories already at `DONE`. UI / device-only / manual-only AC still require production-code wiring (composables called from a route, buttons connected, etc.) -- code that compiles in isolation does NOT satisfy an AC. See `.sage/agents/developer.md` § Your Job for the full contract framing.
3. **Test handling:** follow the rules in `.sage/agents/developer.md` § Test Handling. You MAY fix test bugs (wrong assertions, broken setup, mismatch with AC). You MUST NOT weaken assertions, remove cases, suppress errors, or loosen validation. **Do NOT run tests yourself** -- that's `/sage-tester`'s job. Reason carefully about whether your changes will pass.
4. **Write the AC implementation map sidecar** at `<stories_dir>/STORY-N.implementation.md`. Format, rules, and FORBIDDEN word list are in `.sage/agents/developer.md` § AC Implementation Map (mandatory per story). One `## ACx` heading per AC; under each, list at least one production file path.
5. **Verify the sidecar locally** before flipping status:
   ```bash
   python .sage/_tools/verify_ac_map.py STORY-N --stories-dir <stories_dir>
   ```
   If `success: false`, fix the gaps it reports (actually wire the missing AC, or escalate if an AC truly belongs in another story) BEFORE flipping to TESTING.
6. **Flip `target_story` to `TESTING`** via the helper script -- only after the AC map verifies clean:
   ```bash
   python .sage/_tools/update_story_status.py STORY-N TESTING --stories-dir <stories_dir>
   ```
   Check the JSON return; on `success: false`, stop and report to the user.
7. **Apply the role's Key Rules throughout** -- see `.sage/agents/developer.md` § Key Rules. Highlights: never flip directly to `DONE`, never hand-edit story YAMLs, no scope creep beyond the story's AC, follow project conventions.

If you encounter a blocker (ambiguous requirement, missing dependency, contradiction between AC and tests, AC genuinely belongs in a different story), stop and ask the user inline -- do NOT make assumptions and do NOT silently push the AC forward.

When done, **report to the user as plain text:**

```
Story: <target_story> -> TESTING
AC implemented:
  - AC1: <file:line>, <file:line>
  - AC2: <file:line>
  - AC3: <file:line>
Files changed:
  - <path1>
  - <path2>
Tests targeted:
  - <test_name1>
  - <test_name2>
AC map sidecar: <stories_dir>/STORY-N.implementation.md (verified [OK])
Summary: <one-paragraph description of what you implemented and any decisions/trade-offs>
```

---

## What This Skill Does NOT Do

- Does not run tests (use `/sage-tester` next)
- Does not loop through multiple stories -- handles exactly one per invocation
- Does not create a progress file


---

## Token Tracking (Record)

After reporting to the user, record this skill's estimated token consumption:

```bash
python .sage/_tools/record_worker_usage.py     --feature <feature_name> --role Developer --story <target_story> --cycle 1     --inline --output-chars <approximate output chars produced>
```

Inline-mode entries are flagged `estimated: true` in `_output/<feature_name>/tokens.json` because we can't measure exact tokens from inside the main conversation (use `/usage` for the precise session total). Estimate `output-chars` as roughly the size of files you wrote + your final user-facing report. Failure here is non-fatal -- log and continue.
