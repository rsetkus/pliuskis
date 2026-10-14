---
name: sage-tester
description: Run the Tester agent inline to validate a story's tests (or full regression with --full)
when_to_use: When you want to run tests for a story currently at TESTING and update its status, without running the full team workflow
---

# Sage Tester Skill (inline)

This skill runs the Tester role solo: run tests for a single story (or full regression with `--full`), apply the two-gate logic to decide DONE vs IN_DEV, flip story statuses, and report to the user as plain text.

> **Path note:** All `python .sage/_tools/...` commands below assume an installed project (a `.sage/` directory exists at the project root). If you're running this skill from the sage-feature-team source repo itself (no `.sage/` exists), substitute `_tools/...` instead.

---

## Step 1: Parse Input

Usage:
```
/sage-tester                                # Auto-pick: next story at TESTING; story-scoped
/sage-tester STORY-3                        # Target a specific story; story-scoped
/sage-tester --full                         # Full regression (no story scope; status flips for ALL stories at TESTING)
/sage-tester STORY-3 --full                 # Run full regression but report focused on STORY-3
/sage-tester --feature add_dark_mode STORY-3
```

Compute:
- **explicit_story** -- first positional arg matching `STORY-\d+`, else null
- **scope** -- `"full regression"` if `--full` is given, else `"story"` (only `target_story`'s tagged tests)
- **feature_name** -- `--feature <name>` if given, else auto-detect (Step 3)

---

---

## Step 2: Load Rendered Tester Prompt (for project instructions and role contract)

```bash
python .sage/_tools/load_agents.py full
```

From the JSON, extract `agents.Tester`. The rendered prompt has two kinds of content -- use them differently:

**Use these sections** (mode-agnostic role contract -- they apply to you):
- `.sage/agents/_BASE.md` § Project-Specific Instructions -- the project's test command, log location, parsing patterns, setup/cleanup
- `.sage/agents/tester.md` § Your Job
- `.sage/agents/tester.md` § Key Rules -- monitoring, story-status gating, boundaries

**Ignore these sections** (team-mode workflow that does not apply when invoked as a skill):
- `_BASE.md` § STOP / SILENCE RULE / Starting Message / Workflow / Completion Outcomes / Progress File Updates / Key Rules (All Agents)
- `tester.md` § Tester Workflow (After Receiving Task) -- this skill defines its own workflow below (the two-gate logic is inlined in Step 6)
- `tester.md` § Async Polling Pattern (Universal) -- inline `Monitor` or wait synchronously instead of `ScheduleWakeup`
- `tester.md` § Completion Message Format -- this skill reports to the user as plain text instead

If `success` is false, surface the loader's `error` and stop.

---

## Step 3: Detect Current Feature

If `feature_name` was passed via `--feature`, use it directly. Otherwise:

1. List directories matching `<output_dir>/*/stories/` (output_dir from sage-config.yaml; default `_output`)
2. **Zero matches** -> tell the user: "No <feature>/stories/ directory found." Stop.
3. **Exactly one match** -> use it; extract `feature_name` from the directory name
4. **Multiple matches** -> show the list to the user and ask which feature to work on. Wait for their answer before continuing.

Compute:
- `stories_dir = <output_dir>/<feature_name>/stories/`

---

## Step 4: Determine Target Story

Read every YAML file in `stories_dir`.

**If `explicit_story` was given:**
- Find `<stories_dir>/<STORY-N>.yaml`. Error if missing.
- Validate it's at `status: TESTING`. If not:
  - If `IN_DEV` or earlier: tell the user the story isn't ready for testing -- suggest `/sage-developer <story>` first; offer to abort or proceed anyway.
  - If `DONE`: ask whether to re-test it (regression check), switch story, or abort.

**If no story was given (auto-pick):** call the eligibility script:
```bash
python .sage/_tools/list_eligible.py --feature <feature_name>
```
Take the first story from the `Tester` list (already sorted lowest STORY-N first). If empty and `--full` was not given, show the user the bucketing and stop. Suggest `/sage-developer` to advance an `IN_DEV` story to `TESTING`.

Set `target_story` to the chosen story ID (may be null if `--full` was given without a story).

---

## Step 5: Resolve Story-ID Tagging Convention

You need the project's **story-ID tagging convention** to map test results back to stories. Read it from `.sage/sage-test-creator-config.yaml`. If it's not documented, stop and ask the user -- without it you cannot reliably flip story statuses.

If `scope == "story"`: also use the convention to construct the test selector for `target_story` (e.g., a pytest mark expression `-m "STORY-3"`, or a Jest test name pattern, etc.). The selector mechanism may also be documented in `.sage/sage-tester-config.yaml`. If you can't construct a story-scoped selector, fall back to running full regression and tell the user why.

---

## Step 6: Do the Work

1. **Consult project instructions** for setup, run command, log location, parsing patterns, cleanup.
2. **Execute pre-test setup** if specified.
3. **Run tests:**
   - `scope == "story"`: run only tests tagged for `target_story` using the project's selector idiom (the selector you built in Step 5)
   - `scope == "full regression"`: run the full suite per project instructions
4. **Wait for completion** -- for short runs, just wait. For long-running suites, use the `Monitor` tool to stream output and check periodically. If a test hangs (no output for 30s+), kill the process and report it as a hang -- don't leave the user waiting.
5. **Execute post-test cleanup** if specified.
6. **Parse results** per the project's parsing patterns. Map each test -> story via the tagging convention from Step 5.
7. **For each story you actually exercised that was at `TESTING`, run TWO gates** before deciding DONE vs IN_DEV (NEVER edit YAMLs directly):

   **Gate A: All tagged tests passed?** (Necessary)

   "Tests passed" requires BOTH a successful build AND all tagged tests returning green. **Build/compile/dex failures are Gate A failures, not blockers.** Any of the following -> flip back to `IN_DEV`:
   - Test assertion fail, runtime error, exception during a test
   - Compile error in test or production code
   - Dex/bundling error (R8/D8 rejection, etc.)
   - Test hang (kill the process, treat as failure)
   - Missing fixture/test data the Developer should provide

   ```bash
   python .sage/_tools/update_story_status.py STORY-N IN_DEV --stories-dir <stories_dir> \
       --reason "<one-line summary; prefix build_failure: when compile/dex>"
   ```

   Capture the last 30-50 lines of build/test output for the user-facing report.

   - All passed -> proceed to Gate B.

   **Gate B: AC implementation map sidecar verified?** (Also necessary -- green tests alone do NOT satisfy AC)
   ```bash
   python .sage/_tools/verify_ac_map.py STORY-N --stories-dir <stories_dir>
   ```
   - Returns `success: true` -> flip to `DONE`:
     ```bash
     python .sage/_tools/update_story_status.py STORY-N DONE --stories-dir <stories_dir>
     ```
   - Returns `success: false` -> flip back to `IN_DEV`:
     ```bash
     python .sage/_tools/update_story_status.py STORY-N IN_DEV --stories-dir <stories_dir>
     ```
     **Capture the verifier's JSON output verbatim for the user report** -- it tells the Developer exactly what's missing (missing AC, banned-word hits, AC with no impl path).

   - In `scope == "story"` mode, only run gates for `target_story` (other `TESTING` stories weren't run).
   - Check each `update_story_status.py` return; on `success: false`, surface and continue.

8. **Apply the role's Key Rules throughout** -- see `.sage/agents/tester.md` § Key Rules. Highlights: don't modify test or source code (that's `/sage-developer`'s job), never hand-edit story YAMLs, leave stories outside scope untouched.

When done, **report to the user as plain text:**

```
Scope: <story | full regression>
Story: <target_story or "n/a">
Results: <passed>/<total> passed in <elapsed>s

Story status changes:
  - STORY-X: TESTING -> DONE         (Gate A: tests [OK]  Gate B: AC map [OK])
  - STORY-Y: TESTING -> IN_DEV       (Gate A failed -- failures: <test_names>)
  - STORY-Z: TESTING -> IN_DEV       (Gate A passed but Gate B failed -- see verifier output below)

Test failures:
  - <test_name>: <one-line summary of why it failed>
  ...

AC map failures (per story sent back for AC-map gap):
  - STORY-Z: <verifier JSON verbatim -- missing_ac, banned_word_hits, no_path_ac>
```

---

## What This Skill Does NOT Do

- Does not write or fix code (use `/sage-developer` after a failure)
- Does not loop through fix->test cycles (use `/sage-dev-test` for that, or rerun this skill manually)
- Does not create a progress file


---

## Token Tracking (Record)

After reporting to the user, record this skill's estimated token consumption:

```bash
python .sage/_tools/record_worker_usage.py     --feature <feature_name> --role Tester --story <target_story> --cycle 1     --inline --output-chars <approximate output chars produced>
```

Inline-mode entries are flagged `estimated: true` in `_output/<feature_name>/tokens.json` because we can't measure exact tokens from inside the main conversation (use `/usage` for the precise session total). Estimate `output-chars` as roughly the size of files you wrote + your final user-facing report. Failure here is non-fatal -- log and continue.
