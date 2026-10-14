---
name: sage-test-creator
description: Run the TestCreator agent inline to write tests for the next ready story (or a specific story)
when_to_use: When you want to create tests for a story that has been spec'd but not yet implemented, without running the full team workflow
---

# Sage TestCreator Skill (inline)

This skill runs the TestCreator role solo: write tests for one story's acceptance criteria, tag them by story ID, flip the story to IN_DEV, and report to the user as plain text.

> **Path note:** All `python .sage/_tools/...` commands below assume an installed project (a `.sage/` directory exists at the project root). If you're running this skill from the sage-feature-team source repo itself (no `.sage/` exists), substitute `_tools/...` instead.

---

## Step 1: Parse Input

Usage:
```
/sage-test-creator                    # Auto-pick: next story at TODO with deps DONE
/sage-test-creator STORY-3            # Target a specific story
/sage-test-creator --feature add_dark_mode STORY-3
```

Compute:
- **explicit_story** -- first positional arg matching `STORY-\d+`, else null
- **feature_name** -- `--feature <name>` if given, else auto-detect (Step 3)

---

---

## Step 2: Load Rendered TestCreator Prompt (for project instructions and role contract)

```bash
python .sage/_tools/load_agents.py full
```

From the JSON, extract `agents.TestCreator`. The rendered prompt has two kinds of content -- use them differently:

**Use these sections** (mode-agnostic role contract -- they apply to you):
- `.sage/agents/_BASE.md` § Project-Specific Instructions -- the project's test framework, file location, naming, and story-ID tagging convention
- `.sage/agents/test-creator.md` § Your Job
- `.sage/agents/test-creator.md` § Tests You Cannot Write at Your Seam -- stub-test rules, FORBIDDEN words
- `.sage/agents/test-creator.md` § Story-ID Tagging Convention (Project-Specific) -- mechanism examples, mapping rules
- `.sage/agents/test-creator.md` § Key Rules

**Ignore these sections** (team-mode workflow that does not apply when invoked as a skill):
- `_BASE.md` § STOP / SILENCE RULE / Starting Message / Workflow / Completion Outcomes / Progress File Updates / Key Rules (All Agents)
- `test-creator.md` § TestCreator Workflow (After Receiving Task) -- this skill defines its own workflow below
- `test-creator.md` § Completion Message Format -- this skill reports to the user as plain text instead

If `success` is false, surface the loader's `error` and stop.

---

## Step 3: Detect Current Feature

If `feature_name` was passed via `--feature`, use it directly. Otherwise:

1. List directories matching `<output_dir>/*/stories/` (output_dir from sage-config.yaml; default `_output`)
2. **Zero matches** -> tell the user: "No <feature>/stories/ directory found in <output_dir>. Run /sage-po first to create a spec and stories." Stop.
3. **Exactly one match** -> use it; extract `feature_name` from the directory name
4. **Multiple matches** -> show the list to the user and ask which feature to work on. Wait for their answer before continuing.

Compute:
- `stories_dir = <output_dir>/<feature_name>/stories/`
- `spec_file   = <output_dir>/<feature_name>/spec.md`

---

## Step 4: Determine Target Story

Read every YAML file in `stories_dir` (and `spec_file` for feature-level context).

**If `explicit_story` was given:**
- Find `<stories_dir>/<STORY-N>.yaml`. Error if missing.
- Validate it's at `status: TODO` and its `dependencies:` all resolve to stories with `status: DONE`. If not:
  - If status is already past `TODO`: tell the user and ask whether to proceed anyway (use `--force` on the helper if so), pick a different story, or abort.
  - If a dep is not `DONE`: tell the user the unmet deps and ask whether to proceed anyway, switch story, or abort.

**If no story was given (auto-pick):** call the eligibility script:
```bash
python .sage/_tools/list_eligible.py --feature <feature_name>
```
Take the first story from the `TestCreator` list (it's already sorted lowest STORY-N first). If the list is empty, show the user the `blocked_on_deps` and `all_statuses` fields so they see why nothing's eligible, then stop. **Do NOT eyeball the YAMLs yourself; trust the script's bucketing.** A dependency is satisfied only when `status == "DONE"` -- `TESTING` and `IN_DEV` don't count.

Set `target_story` to the chosen story ID.

---

## Step 5: Do the Work

1. **Read project instructions** for test framework, file location, naming, and **story-ID tagging convention**. If the project instructions don't specify a tagging convention, **ask the user before proceeding** -- don't pick one yourself; Tester needs to use the same one.
2. **Read `target_story`'s YAML** to get its `acceptance_criteria:` block (the contract for the tests you write). If the AC list is empty, stop and ask the user.
3. **Flip `target_story` to `CREATE_TESTS`** via the helper script:
   ```bash
   python .sage/_tools/update_story_status.py STORY-N CREATE_TESTS --stories-dir <stories_dir>
   ```
   Check the JSON return; on `success: false`, stop and report.
4. **Write the test file(s)** for `target_story`'s AC, using the project's framework, location, naming, and tagging convention. Tag/group test functions by `target_story`'s ID so the mapping is recoverable from the test file alone -- see `.sage/agents/test-creator.md` § Story-ID Tagging Convention (Project-Specific) for examples and rules.
5. **Handle AC you cannot test at your seam** (UI without UI test seam, manual-QA AC, device-only AC) per `.sage/agents/test-creator.md` § Tests You Cannot Write at Your Seam: write a stub test at the appropriate location, marked to skip in the default suite. The FORBIDDEN word list ("deferred", "future", "later", "next pass", etc.) applies to your reporting too.
6. **Apply the role's Key Rules throughout** -- see `.sage/agents/test-creator.md` § Key Rules. Highlights: NEVER set a story to `IN_DEV` without an actual test for every AC, NEVER touch stories outside the target, NO test execution, NO code implementation.
7. **Flip `target_story` from `CREATE_TESTS` to `IN_DEV`** via the helper script:
   ```bash
   python .sage/_tools/update_story_status.py STORY-N IN_DEV --stories-dir <stories_dir>
   ```

When done, **report to the user as plain text:**

```
Story: <target_story> -> IN_DEV
AC covered: AC1, AC2, ...
Tests written: <count>
Test file(s):
  - <path>
Test functions:
  - <name1>
  - <name2>
  ...
Stub tests (if any): <path -- marked skip; targets AC that can't run at this seam>
```

---

## What This Skill Does NOT Do

- Does not run Developer or Tester (use `/sage-developer` and `/sage-tester` next)
- Does not loop through multiple ready stories -- handles exactly one per invocation
- Does not create a progress file


---

## Token Tracking (Record)

After reporting to the user, record this skill's estimated token consumption:

```bash
python .sage/_tools/record_worker_usage.py     --feature <feature_name> --role TestCreator --story <target_story> --cycle 1     --inline --output-chars <approximate output chars produced>
```

Inline-mode entries are flagged `estimated: true` in `_output/<feature_name>/tokens.json` because we can't measure exact tokens from inside the main conversation (use `/usage` for the precise session total). Estimate `output-chars` as roughly the size of files you wrote + your final user-facing report. Failure here is non-fatal -- log and continue.
