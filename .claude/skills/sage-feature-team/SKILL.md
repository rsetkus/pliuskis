---
name: sage-feature-team
description: Complete feature development workflow (ProductOwner -> parallel TestCreator/Developer/Tester per story)
when_to_use: When you want to build a complete feature from requirements through specification, tests, implementation, and validation -- with multiple stories worked in parallel
---

# Sage Feature Team Skill

You are the **Team Lead / Orchestrator** for a multi-agent feature development workflow. You run in the main conversation; worker agents report back to you (`@User`).

> **Path note:** All `python .sage/_tools/...` and `python _tools/...` commands below assume an installed project (a `.sage/` directory exists at the project root). If you're running this skill from the sage-feature-team source repo itself (no `.sage/` exists), substitute `_tools/...` instead. The agent prompts you spawn already have this path baked in by `load_agents.py` via `{SAGE_TOOLS_DIR}` -- you don't need to fix them.

The workflow has two phases:

1. **Phase 1 -- ProductOwner (single agent, sequential).** A long-lived ProductOwner agent creates the spec, the epic YAML files (at least one), and per-story YAML files; iterates on user feedback; and waits for `APPROVED`.
2. **Phase 2 -- Parallel scheduler with verification checkpoints.** Once stories are approved, you become a scheduler. You scan the stories directory and **spawn ephemeral, per-story worker agents** (one per ready story, up to `max_parallel_workers`). Each worker handles one story, reports completion, and is shut down. You re-scan and spawn the next batch.

   When all stories within an **epic** reach `DONE`, you spawn an **EpicVerifier** worker for that epic to run the verification checkpoint. On success the epic flips to `VERIFIED` and downstream epics become eligible. On failure, specific stories are re-opened with details. The workflow ends when every epic is `VERIFIED`, or stories/epics are escalated.

You don't decide HOW work is done in any particular project -- that comes from each agent's `.sage/sage-<agent>-config.yaml`, which the loader bakes into the agent prompts.

---

## Architectural Reminder

```
sage-config.yaml          <- team/paths/limits config
.sage/sage-<role>-config  <- per-agent project instructions (instructions list)
agents/_BASE.md           <- shared protocol + {PROJECT_INSTRUCTIONS} hook
agents/<role>.md          <- generic job description
   |
   v  load_agents.py assembles base + role + instructions
   v
Agent prompt (fully rendered) -- same prompt reused for every worker of that role
```

Stories live as **per-story YAML files**:
```
_output/<feature_name>/stories/STORY-1.yaml
_output/<feature_name>/stories/STORY-2.yaml
...
```

Epics live as **per-epic YAML files** alongside stories:
```
_output/<feature_name>/epics/EPIC-1.yaml
_output/<feature_name>/epics/EPIC-2.yaml
...
```

Every feature has at least one epic (PO writes at minimum `EPIC-1.yaml`). Every story file has an `epic: EPIC-N` field naming its parent epic.

Verification artifacts (written by EpicVerifier) live at:
```
_output/<feature_name>/verification/EPIC-N.md
```

Each YAML carries its own status. Concurrent workers update their own story file via `.sage/_tools/update_story_status.py` (locked, atomic); epic YAMLs are flipped via `.sage/_tools/update_epic_status.py`.

---

## Step 1: Parse Input

From the user's invocation, compute these values once and reuse them throughout:

- **mode** -- `dev-test-only` if `--dev-test-only` flag present (with optional `--full-regression` or `--targeted <test_names>`), else `full`
- **feature_name** -- full mode: extract from user's text and convert to snake_case (e.g., "Add Dark Mode" -> `add_dark_mode`); dev-test mode: `dev_test_cycle`
- **output_dir** -- from `sage-config.yaml` -> `paths.output_dir` (typically `_output`)
- **max_cycles** -- from `--max-cycles N` if given, else from `sage-config.yaml` -> `limits.max_cycles` (per-story dev<->test cap)
- **max_parallel_workers** -- `1` if `--serial` flag present, else from `sage-config.yaml` -> `limits.max_parallel_workers` (default 4). `--serial` forces strict one-at-a-time execution (story workers AND EpicVerifier workers); useful for debugging, watching the flow step by step, or avoiding test-isolation collisions in projects where concurrent test runs interfere.
- **global_timeout_seconds** -- from `sage-config.yaml` -> `limits.global_timeout_seconds` (default 3600)
- **spec_file** -- `<output_dir>/<feature_name>/spec.md`
- **epics_dir** -- `<output_dir>/<feature_name>/epics/`
- **stories_dir** -- `<output_dir>/<feature_name>/stories/`
- **verification_dir** -- `<output_dir>/<feature_name>/verification/` (created on first verifier run)
- **progress_file** -- `<output_dir>/<feature_name>/progress.md`

**`--resume <feature_name>`** continues from existing artifacts (spec, epics, stories, verification on disk). The resume contract:

- **Cycle budgets reset on resume.** A story that burned 3/3 dev<->test cycles before the interrupt gets a fresh `max_cycles` budget after resume. Same for epic verifier `verify_cycle_count`. This is deliberate: invoking `--resume` is itself a signal that you've decided to keep going, often after fixing something out-of-band. If a story is genuinely unsolvable, you'll notice within a few resumed cycles and intervene manually (edit the spec, fix config, or mark the story BLOCKED by hand).
- **YAML status persists.** Stories at TODO/CREATE_TESTS/IN_DEV/TESTING/DONE/BLOCKED resume in those states. Epics at TODO/IN_PROGRESS/DONE/VERIFIED/BLOCKED do the same. BLOCKED stories/epics stay BLOCKED until you unblock them via `update_story_status.py` or `update_epic_status.py`.
- **In-flight workers do NOT survive.** Anything that was mid-task at the moment of interrupt is gone -- subagents don't outlive the parent session in practice, and even if they did, the team reconciliation step below force-cleans them. The next scheduling tick will re-spawn fresh workers for stories that need them based on their on-disk status.
- **Team reconciliation (mandatory on resume).** Before normal Step 4 TeamCreate, the resume branch MUST first call `TeamDelete(team_name=<configured team name>)` to clean up any leaked team + orphan workers from the prior crashed run. Treat any "team not found" error as success -- the call is idempotent; the goal is to guarantee the team name is clean and available before normal Step 4 spawns into it. Without this, `TeamCreate` may error on a name collision, or worse, succeed into a team that still contains orphan workers from the prior run, causing name collisions and ambiguous routing on subsequent spawns.

Also: capture `feature_start_time = now()` here -- even on resume, this resets to now (the global wall-clock budget starts fresh). You'll use this when invoking `discover_and_record.py` so it scopes to transcripts from this run only (otherwise it'd sweep the project's entire agent history).

When you send messages to agents below, write the message naturally with these literal values inlined -- not Python f-string syntax with `{feature_name}` placeholders.

---

## Step 2: Load Agent Prompts

```bash
python .sage/_tools/load_agents.py full
```

Expected JSON: `success: true`, `agents` containing rendered prompts for `ProductOwner`, `TestCreator`, `Developer`, `Tester`. Validate as in the previous version of this skill; if `success: false`, surface the error and stop.

You will reuse the rendered `TestCreator`, `Developer`, and `Tester` prompts as the prompt for every per-story worker of that role. The ProductOwner prompt is used once for Phase 1.

---

## Step 3: Preflight

- `sage-config.yaml` was readable (loader confirmed)
- `output_dir` exists (or create it)
- For full mode with a feature name: no spec/stories collision (or use `--resume`)

Don't preflight project-specific things (test runners, servers). Those belong to the agents' `.sage/` instructions.

---

## Step 4: Create Team

**On resume, reconcile first.** If this invocation includes `--resume`, attempt to delete any leaked team from a prior crashed run BEFORE creating the fresh one. `TeamDelete` cascades -- it shuts down every member agent before tearing down the team. The call is idempotent; treat any "team not found" error as success:

```python
# Resume only -- skip on a clean first run.
try:
    TeamDelete(team_name=team_name)
except Exception:
    # "team not found" is the happy path -- prior run cleaned up properly.
    # Any other error: log it and proceed -- TeamCreate below will surface a
    # real collision if one exists.
    pass
```

Then create the team fresh (this is the normal path for both first runs and resumes after the cleanup above):

```python
TeamCreate(team_name=team_name, description="Sage feature development team")
```

---

## Step 5: Phase 1 -- ProductOwner (sequential, single agent)

Skip this whole step in `dev-test-only` mode -- go straight to Phase 2 (Step 6) with the existing stories directory.

### 5a. Spawn ProductOwner

```python
Agent(
  name="ProductOwner",
  prompt=agents["ProductOwner"],
  team_name=team_name,
  subagent_type="general-purpose"
)
```

Wait 2-3 seconds. The agent idles until you SendMessage it.

### 5b. Send the spec task

SendMessage to `ProductOwner`:
- `@User: [Feature: <feature_name>]` opener
- `[Task: po-spec-<feature_name>]`
- The user's requirements (verbatim)
- `Spec file: <spec_file>`
- `Stories dir: <stories_dir>`
- `Progress file: <progress_file>`
- `Reference: HANDBOOK.md`

Run **starting-message + completion monitoring** (Step 8).

### 5c. Forward questions to the User; require explicit APPROVED

- When ProductOwner asks questions: forward them to the User; relay the answer back. Don't answer for the user.
- When ProductOwner reports the spec is ready: forward to the User: "ProductOwner has completed the spec -- please review and reply APPROVED to proceed." Wait for explicit `APPROVED`.

### 5d. Record token usage and shut down ProductOwner

After ProductOwner's completion message arrives and you've routed approval back to the User:

1. **Record token usage via discovery** (mechanical, idempotent -- doesn't depend on you remembering anything per-spawn):
   ```bash
   python .sage/_tools/discover_and_record.py --feature <feature_name>
   ```

   Discovery defaults to **current-session-only scope** -- it auto-detects which `~/.claude/projects/<slug>/<session-uuid>/` is the active one (the dir with most recent activity) and only scans that. This matches `/usage` semantics (current Claude Code session). No `--since-minutes` needed in the common case.

   If multiple discrete workflow runs happened in the same Claude Code session and you want only the latest one, add `--since-minutes <ceil((now-feature_start_time)/60) + 5>` to further narrow.

   Discovery walks `~/.claude/projects/<slug>/<current-session>/subagents/`, finds every sage worker transcript (filters out built-in `Explore`/`Plan`/etc. agents), reads each `.meta.json` to identify the worker, and records any not yet in `<output_dir>/<feature_name>/tokens.json`. Re-renders `<feature_name>/tokens.md`. Safe to call multiple times; existing entries are skipped. If discovery returns `success: false`, log it and continue -- never block the workflow on telemetry.

2. **Shut down the agent.** The ONLY way to actually remove a teammate from the team panel is to send `shutdown_request`; the teammate responds with `shutdown_response approve=true` and their process terminates. A "you are released" plain-text message does NOT shut anyone down -- the agent stays idle in the team panel. `TaskStop` doesn't work either.

   ```python
   SendMessage(
     to="ProductOwner",
     message={"type": "shutdown_request", "reason": "Phase 1 complete -- spec approved, releasing"}
   )
   ```

   Wait briefly (up to ~10s) for ProductOwner's `shutdown_response approve=true`. Their process terminates on approve and they leave the team panel. Remove `ProductOwner` from your tracked `spawned_workers` set (initialized in Step 6a). If the worker doesn't respond or sends `approve=false`, log it -- Step 9 (Disband Team) will retry shutdown for any survivors before calling `TeamDelete`.

---

## Step 6: Phase 2 -- Parallel Scheduler

You now own a scheduling loop. You read the stories directory, spawn per-story workers, wait for any to complete, and re-scan. The loop ends when every story is `DONE` (or escalated as terminal `BLOCKED`/cycle-exhausted), or the global timeout fires.

### 6a. Initialize state

In your own working memory (no need to write to disk), maintain:

```
in_flight             = {}   # STORY-N or EPIC-N -> {role, agent_name, started_at, cycle_n}
spawned_workers       = set()  # every worker name you've Agent()-spawned and not yet confirmed shut down (incl. ProductOwner if Phase 1 ran). Source of truth for Step 9 teardown.
cycle_count           = {}   # STORY-N -> int (Developer->Tester rounds completed; counts when Tester or EpicVerifier re-flips to IN_DEV)
verify_cycle_count    = {}   # EPIC-N -> int (EpicVerifier re-runs after FAILED outcomes)
escalated             = set()  # STORY-Ns AND EPIC-Ns that hit max_cycles or are unrecoverable
start_time            = now()
```

When you `Agent(...)` to spawn a worker, add `worker_name` to `spawned_workers`. When you confirm a worker has shut down (received `shutdown_response approve=true`), remove it.

### 6b. Scheduling rule (per scan)

**At the top of every scan, run discovery to record any worker token usage that completed since the last scan:**

```bash
python .sage/_tools/discover_and_record.py --feature <feature_name>
```

Defaults to current Claude Code session only (auto-detected). This matches `/usage` semantics and prevents pulling in transcripts from previous interrupted runs in different sessions. No flags needed in the common case.

If you want to additionally narrow to a sub-window of the current session (e.g., multiple workflow runs in the same session), pass `--since-minutes <ceil((now-feature_start_time)/60) + 5>` based on `feature_start_time` from Step 1.

One call per scan. Mechanical and idempotent -- it walks the current session's subagent directory, filters out built-in `Explore`/`Plan` agents, finds every sage worker transcript, and records any not yet in `<output_dir>/<feature_name>/tokens.json`. This is the **only** mechanism by which token usage gets recorded -- no per-worker recording calls anywhere else in this skill. Re-rendering of `<feature_name>/tokens.md` happens automatically as a side effect of any new entries being added.

If discovery returns `success: false`, log it and proceed -- don't block the scheduler on telemetry.

---

**Compute eligibility mechanically -- do NOT eyeball the YAMLs.** Call the eligibility script:

```bash
python .sage/_tools/list_eligible.py --feature <feature_name>
```

Returns JSON like:
```json
{
  "success": true,
  "TestCreator":     ["STORY-2"],            // TODO + every dep at status DONE (incl. epic_dep at VERIFIED)
  "Developer":       ["STORY-4", "STORY-5"], // IN_DEV + every dep at status DONE
  "Tester":          ["STORY-1"],            // TESTING + every dep at status DONE
  "in_progress":     ["STORY-7"],            // CREATE_TESTS (TestCreator owned it; resume-only)
  "blocked_on_deps": {"STORY-3": ["STORY-1 (TESTING, needs DONE)", "epic_dep:EPIC-2 -> EPIC-1 (DONE, needs VERIFIED)"]},
  "blocked":         ["STORY-6"],            // status BLOCKED
  "done":            [],
  "all_statuses":    {"STORY-1": "TESTING", ...},
  "epics":           {"EPIC-1": {"status": "DONE", "rollup": "DONE", ...}, ...},
  "epic_ready_to_verify":   ["EPIC-1"]       // all stories DONE, on-disk status != VERIFIED -- spawn EpicVerifier
}
```

**The script's lists are authoritative.** Spawn workers only for stories that appear in `TestCreator`, `Developer`, or `Tester`, AND spawn EpicVerifier workers for any epic in `epic_ready_to_verify`. Never spawn for a story that's only in `blocked_on_deps`, `blocked`, `in_progress`, or `done` -- those are explicitly NOT eligible.

**Critical rule the script enforces:** a dependency is satisfied **only** when its `status == "DONE"`. `TESTING` is NOT close enough. `IN_DEV` is NOT close enough. Only `DONE`. This matters on resume (e.g., you interrupted last run while STORY-1 was at TESTING): downstream TODO stories that depend on STORY-1 stay in `blocked_on_deps` until STORY-1 actually reaches DONE.

After getting the JSON: skip any story whose ID is in `in_flight` (you're already running a worker for it) or `escalated` (per-story max_cycles exhausted). Sort the remaining eligible stories by ID (lowest STORY-N first).

Quick reference of what the script returns vs. action:

| Bucket | Action |
|---|---|
| `TestCreator` | Eligible -- spawn `TestCreator-<STORY-N>` |
| `Developer`   | Eligible -- spawn `Developer-<STORY-N>` (add `-cN` on re-cycles) |
| `Tester`      | Eligible -- spawn `Tester-<STORY-N>` (story-scoped, story-only) |
| `epic_ready_to_verify` | Eligible -- spawn `EpicVerifier-<EPIC-N>` (one per epic; first invocation has no `-cN` suffix; re-runs add `-cN`) |
| `in_progress` | CREATE_TESTS state -- typically left over from interrupt. Treat as in-flight. If it's stale (no real worker), flip back to TODO via `update_story_status.py` to let it re-trigger. |
| `blocked_on_deps` | Skip. Will be re-evaluated next scan after its deps reach DONE/VERIFIED. |
| `blocked`     | Skip. User must resolve out-of-band (edit YAML, `--resume`). |
| `done`        | Skip. |

Available slots = `max_parallel_workers - len(in_flight)`. Spawn `min(slots, len(eligible))` workers. Story workers and EpicVerifier workers share the same parallel budget.

If `len(in_flight) == 0` AND no eligible stories AND no epic ready to verify AND not every epic is `VERIFIED`: **deadlock**. Escalate (Step 6f).

### 6c. Spawn a per-story worker (or EpicVerifier worker)

Worker name pattern: `<Role>-<STORY-N>` for story workers (e.g., `TestCreator-STORY-3`, `Developer-STORY-7`, `Tester-STORY-2`) or `EpicVerifier-<EPIC-N>` for verifier workers. Names must be unique within the team -- if a worker for the same story+role (or same epic) was previously spawned and shut down, append a `-cN` suffix where N is the cycle count for that scope (e.g., `Developer-STORY-3-c2`, `EpicVerifier-EPIC-1-c2`).

```python
Agent(
  name=worker_name,
  prompt=agents[role],         # rendered prompt for the role; same for every worker of that role
  team_name=team_name,
  subagent_type="general-purpose"
)
spawned_workers.add(worker_name)
# wait ~2s
SendMessage(to=worker_name, summary="...", message=<task message>)
```

Track `in_flight[STORY-N] = {role, agent_name: worker_name, started_at: now(), cycle_n: cycle_count[STORY-N]}`.

### 6d. Per-role task message templates

All worker task messages should be self-contained -- workers don't share state with each other. Always pass the story id, paths, and references explicitly.

**Pre-fetched payload (every task message includes one).** Before sending a task message, render a payload via `prepare_task_payload.py` and embed its stdout directly into the message body. This eliminates the 3-5 `Read` tool calls a worker would otherwise make at task start (spec, story YAML(s), and optional epic YAML) -- which trims cache_create on the worker's bootstrap turns. The orchestrator already has this content available (list_eligible.py loaded the YAMLs); shipping it inline costs nothing on the orchestrator side. The pointer lines (Spec file, Stories dir, etc.) stay -- agents may still Read additional files (test files, project instruction files) not covered by the payload.

```bash
python .sage/_tools/prepare_task_payload.py --feature <feature_name> --stories STORY-N[,STORY-M] [--epic EPIC-N]
```

Capture stdout and embed it verbatim near the bottom of the task message body, before the `Reference:` line.

#### TestCreator worker (for a TODO story)

SendMessage:
- `@User: [Feature: <feature_name>] (Story: <STORY-N>)` opener
- `[Task: tc-<STORY-N>-<feature_name>]`
- `Target story: <STORY-N>` (this is your single target -- no auto-discovery)
- `Stories dir: <stories_dir>`
- `Spec file: <spec_file>`
- `Progress file: <progress_file>`
- **Payload:** embed stdout of `prepare_task_payload.py --feature <feature_name> --stories <STORY-N>`
- `Reference: HANDBOOK.md`

#### Developer worker (for an IN_DEV story)

SendMessage:
- `@User: [Feature: <feature_name>] (Story: <STORY-N>, Cycle: <cycle_n>/<max_cycles>)` opener
- `[Task: dev-<STORY-N>-c<cycle_n>-<feature_name>]`
- `Target story: <STORY-N>`
- `Stories dir: <stories_dir>`
- `Spec file: <spec_file>`
- `Progress file: <progress_file>`
- If `cycle_n > 1`: paste the previous Tester run's `TEST_FAILURE` lines for this story verbatim. If the previous Tester reported the story came back to `IN_DEV` because the **AC implementation map gate failed** (Gate B), paste the `verify_ac_map.py` JSON verbatim too -- those are the gaps the Developer must close this cycle.
- Reminder: `Required artifact: <stories_dir>/STORY-<N>.implementation.md (run verify_ac_map.py before claiming COMPLETE).`
- **Payload:** embed stdout of `prepare_task_payload.py --feature <feature_name> --stories <STORY-N>`
- `Reference: HANDBOOK.md`

#### Tester worker (for a TESTING story -- story-scoped)

SendMessage:
- `@User: [Feature: <feature_name>] (Story: <STORY-N>)` opener
- `[Task: tester-<STORY-N>-c<cycle_n>-<feature_name>]`
- `Target story: <STORY-N>`
- `Test scope: story <STORY-N>` (LITERAL -- Tester role file uses this to construct a story-scoped selector and to know it must only flip this story)
- `Stories dir: <stories_dir>`
- `Progress file: <progress_file>`
- **Payload:** embed stdout of `prepare_task_payload.py --feature <feature_name> --stories <STORY-N>`
- `Reference: HANDBOOK.md`

The Tester role file has the story-scoped selector logic: it reads the project's tagging convention from `.sage/sage-test-creator-config.yaml` and runs only that story's tests. Multiple Tester workers can run concurrently as long as the project's test isolation allows it.

#### EpicVerifier worker (for an epic with all stories DONE)

SendMessage:
- `@User: [Feature: <feature_name>] [EPIC-N]` opener
- `[Task: verify-EPIC-N-<feature_name>]`
- `Epic: EPIC-N`
- `Feature: <feature_name>`
- `Stories in scope: STORY-1, STORY-2, ...` (the list from the epic's `story_ids:`)
- `Verification artifact: <verification_dir>/EPIC-N.md`
- `Stories dir: <stories_dir>`
- `Epics dir: <epics_dir>`
- **Payload:** embed stdout of `prepare_task_payload.py --feature <feature_name> --stories STORY-1,STORY-2,... --epic EPIC-N` (all stories in epic.story_ids, plus the epic itself)
- `Reference: HANDBOOK.md`

The EpicVerifier role file handles the `verify_epic.py` precondition gate, the cross-story regression run (using the project's tagging convention to scope), the optional epic-level acceptance interpretation, the verification artifact write, and the `update_epic_status.py EPIC-N VERIFIED` flip. Story re-opens on failure happen through `update_story_status.py` exactly the way the Tester re-opens them.

### 6e. Wait for ANY worker to complete; reschedule

After spawning a batch, you wait. Don't block on a specific worker -- wait on the team and act on whichever completes first.

For each worker completion message:

1. **Re-read the source-of-truth YAML.**
   - For a story worker: re-read the completed story's YAML -- the worker already flipped it via `update_story_status.py`.
   - For an EpicVerifier worker: re-read the epic's YAML AND every story in the epic (the verifier may have re-opened stories on failure).
2. **Update bookkeeping based on the new state:**

   **For story workers:**
   - **Story is now `IN_DEV`** (Tester re-cycled it, or Developer-after-TestCreator):
     - If the previous worker was Tester: `cycle_count[STORY-N] += 1`. The Tester's completion payload tells you *why* -- Gate A (test/build/compile/dex failure) or Gate B (AC implementation map gate). **Trust the verdict; don't re-verify.** Carry the failure details forward (failing test list, build error excerpt, or `verify_ac_map.py` JSON) into the next Developer task message verbatim. If `cycle_count[STORY-N] > max_cycles`: add to `escalated` and run per-story escalation (Step 6f).
     - If the previous worker was TestCreator: ready for Developer next scan. TestCreator may also have written stub tests for AC its seam can't cover.
     - If the previous worker was EpicVerifier (cross-story regression or AC map regression re-opened the story): `cycle_count[STORY-N] += 1`. Carry the verifier's failure details into the next Developer task message. If `cycle_count > max_cycles`: escalate per Step 6f.
   - **Story is now `DONE`** (from Tester): both gates passed. Nothing more for this story.
   - **Story is now `TESTING`** (from Developer): ready for Tester next scan. (Developer will not have flipped without first running `verify_ac_map.py` and getting success.)
   - **Story is now `BLOCKED`** (any worker reported Outcome 3 -- truly unrecoverable): the worker already marked it BLOCKED with a blocker reason in the YAML and included matching details in the completion message. **Don't try to fix it or re-cycle.** Add to `escalated`, log the blocker, surface to User immediately:
     ```
     @User: [Feature: <feature_name>] STORY-N marked BLOCKED by <Role>
     Reason: <blocker reason from completion payload>
     Details: <relevant context from worker's report>
     Continuing other stories. Resolve this one out-of-band (edit spec, fix config, etc.) and re-run with --resume.
     ```
     Continue the scheduler -- one BLOCKED story doesn't stop the rest.

   **For EpicVerifier workers:**
   - **Epic is now `VERIFIED`** (success): the verifier wrote `<verification_dir>/EPIC-N.md` and flipped the epic YAML. Downstream epics that depend on this one are now eligible (next scan will surface their stories).
   - **Verifier reported `FAILED`** (stories re-opened to IN_DEV with `cross_story_regression` or `ac_map_regression` reasons): the verifier did NOT write the artifact and did NOT flip the epic. Track `verify_cycle_count[EPIC-N] += 1`. If `verify_cycle_count[EPIC-N] > max_cycles`: add `EPIC-N` to `escalated` and escalate (Step 6f) -- the same `max_cycles` budget gates verifier re-runs. Otherwise the next scan will see the re-opened stories at IN_DEV and route them back to Developer. After those stories cycle through Tester and reach DONE again, the epic will re-surface in `epic_ready_to_verify` and trigger another EpicVerifier-EPIC-N-cN run.
   - **Verifier reported `BLOCKED`** (e.g., epic acceptance gap with no owning story, missing tagging convention): the verifier already flipped the epic YAML to BLOCKED with a reason. Add the epic to `escalated`, surface to User immediately:
     ```
     @User: [Feature: <feature_name>] EPIC-N verification BLOCKED
     Reason: <blocker reason from verifier completion payload>
     Continuing other epics. Resolve out-of-band (amend spec, add story, etc.) and re-run with --resume.
     ```
     Continue the scheduler -- one BLOCKED epic doesn't stop the rest.

3. **Shut down the worker** to actually remove it from the team panel:
   ```python
   SendMessage(
     to=worker_name,
     message={"type": "shutdown_request", "reason": "task complete -- releasing worker"}
   )
   ```
   Wait briefly (up to ~10s) for the worker's `shutdown_response approve=true`. Their process terminates on approve and they disappear from the team panel. Once confirmed, remove `worker_name` from `spawned_workers`. If they don't respond or send `approve=false`, log the worker name -- Step 9 (Disband Team) will retry shutdown for any survivors at the end of the workflow. **Never skip this step**: without it, the worker stays idle in the team panel indefinitely. (Note: `TaskStop` does NOT remove agents from the team panel -- only `shutdown_request` does.)
4. **Remove from `in_flight`** and re-run the scheduling rule (6b) to pick up newly eligible stories.

### 6f. Per-story escalation

When a story hits `max_cycles` (Developer<->Tester loops without success) or a worker reports an unrecoverable BLOCKER:

- Add it to `escalated`
- Mark its YAML `BLOCKED` via the helper (with a reason):
  ```bash
  python .sage/_tools/update_story_status.py STORY-N BLOCKED \
      --stories-dir <stories_dir> --reason "max_cycles exceeded after <n> rounds"
  ```
- Report to the User: `@User: [Feature: <feature_name>] STORY-N escalated -- <reason>. Continuing other stories.`
- Continue the scheduler -- one stuck story doesn't stop the rest

### 6g. Global wall-clock kill switch

At the top of every scan, check `now() - start_time >= global_timeout_seconds`. If hit:

- Stop spawning new workers
- Wait briefly (one more tick) for in-flight workers to complete naturally
- Mark every still-non-DONE story as `BLOCKED` with reason `global_timeout`
- Jump to Step 7 (Final Report) with `Status: GLOBAL_TIMEOUT`, then **Step 9 (Disband Team)** -- the catch-all that sends `shutdown_request` to every surviving worker in `spawned_workers` and calls `TeamDelete`.

### 6h. Loop exit

The scheduler loop exits cleanly when:
- Every epic in `epics_dir` is `status: VERIFIED` -> success
- OR every remaining non-VERIFIED epic is in `escalated` (and every non-DONE story in non-escalated epics is also DONE) -> partial success
- OR the global timeout fired -> escalation

---

## Step 7: Final Report

Worker token usage was recorded incrementally by `discover_and_record.py` at every scheduling scan (Step 6b). The orchestrator's own main-conversation cost isn't tracked in the per-feature TOKENS file -- it's part of the user's session total (visible via Claude Code's `/usage` command).

Before sending the report, regenerate the human-readable rollup so the user has a fresh snapshot to compare against:
```bash
python .sage/_tools/rollup_status.py --feature <feature_name> --write
```
This rewrites `<output_dir>/<feature_name>/progress.md` from the authoritative YAML state. Non-fatal -- if it fails, log and proceed.


### Success (Full Mode -- all epics VERIFIED)

```
@User: [Feature: <feature_name>] Feature Development Complete

Status: SUCCESS
Stories: <N> total, all DONE
Epics: <M> total, all VERIFIED
Cycles used (per story): STORY-1=1, STORY-2=2, ...
Verify cycles (per epic): EPIC-1=1, EPIC-2=2, ...
Wall clock: <elapsed>s

Artifacts:
- Spec:             <spec_file>
- Epics dir:        <epics_dir>
- Stories dir:      <stories_dir>
- Verification dir: <verification_dir>
- Tests:            see git diff
- Code:             see git diff
```

### Partial Success (some stories or epics escalated)

```
@User: [Feature: <feature_name>] Feature Development Partially Complete

Status: PARTIAL
Stories DONE:      STORY-1, STORY-3, ...
Stories ESCALATED: STORY-2 (max_cycles), STORY-5 (BLOCKED: ...)
Epics VERIFIED:    EPIC-1, EPIC-3, ...
Epics ESCALATED:   EPIC-2 (max_verify_cycles), ...

See <stories_dir> for blocked_reason on each escalated story.
See <epics_dir> for blocked_reason on each escalated epic.
```

### Global Timeout

```
@User: [Feature: <feature_name>] Workflow Hit Global Timeout

Status: GLOBAL_TIMEOUT after <global_timeout_seconds>s
Stories DONE: ...
Stories left: ...
```

### Dev-Test Mode

(Phase 1 skipped; Phase 2 still applies but works against the existing stories directory. If no stories directory exists, fall back to the legacy `/sage-dev-test` flow -- recommend the user invoke that skill instead.)

---

## Step 8: Monitoring (Starting Message + Completion)

You watch two events per worker: the starting message (within 60s, with one nudge re-send at 30s) and the completion message (within the work timeout). Both arrive as plain `SendMessage`s. No SYN/SYN-ACK/ACK, no message-ID dedup -- but the starting handshake gets ONE retry because freshly-spawned workers occasionally miss the first task delivery (race between `Agent()` spawn and the first `SendMessage`).

### Starting-message monitoring (every task you send)

| T | Action |
|---|---|
| 0-30s | Wait for the worker's `Starting on STORY-N` SendMessage |
| 30s   | **Nudge**: re-send the EXACT same task message (same `summary` and `message` content). Don't change the content -- the agent's prompt tells it to treat a duplicate task message as confirmation, not as a new task. Continue waiting up to T=60s. Log the nudge so you know one was sent (don't re-nudge again -- one nudge only). |
| 30-60s | Wait for the worker's `Starting on STORY-N` SendMessage |
| 60s   | **Escalate**: send `SendMessage(to=worker, message={"type": "shutdown_request", "reason": "ack_timeout"})`, mark its story `BLOCKED` with reason `ack_timeout`, remove from `spawned_workers` once shutdown confirmed, continue scheduling. |

The 30s nudge exists because the spawn-then-immediately-SendMessage pattern has a measured-in-practice failure mode where the first task message doesn't register at the worker. One re-send fixes it reliably; if it doesn't, the worker is genuinely dead. Don't escalate before nudging; don't nudge more than once.

Use `ScheduleWakeup` so you don't block while waiting; or use `Monitor` on the team to receive worker messages reactively.

### Completion monitoring (after the starting message arrives)

| T | Action |
|---|---|
| 0-`timeout_work_hard`s (default 480s = 8min) | Wait for the completion message |
| `timeout_work_hard / 2` | Optional gentle status check (plain `SendMessage`); the worker is silent during work but can answer queries if reachable |
| `timeout_work_hard` | **Escalate**: send `SendMessage(to=worker, message={"type": "shutdown_request", "reason": "work_timeout"})`, mark its story `BLOCKED` with reason `work_timeout`, remove from `spawned_workers` once shutdown confirmed, continue scheduling |

### On completion message receipt

1. Re-read the story YAML -- that's the source of truth. The worker already flipped it.
2. Route per Step 6e based on the new status.
3. Send `shutdown_request`; on `shutdown_response approve=true`, remove from `spawned_workers`.

That's the whole loop. No handshake state, no message-ID tracking, no dedup table. If a duplicate completion message arrives somehow, re-reading the YAML is idempotent (no status change -> no new routing).

---

## Step 9: Disband Team (always runs, every exit path)

**This step runs unconditionally** after Step 7 (Final Report) -- success path, partial-success path, global-timeout path, AND the deadlock escalation path. The user invoked the skill; the skill owns the team's lifecycle from `TeamCreate` (Step 4) to `TeamDelete` here.

1. **Identify survivors:** any worker name still in `spawned_workers` after Steps 5d and 6e ran. Ideally this set is empty by the time you reach Step 9 -- workers should have been shut down individually at each completion. But starting-message timeouts, work timeouts, deadlock escalations, and missed shutdown_response approvals can leave stragglers.

2. **Send `shutdown_request` to every survivor** and collect responses:
   ```python
   for worker in survivors:
       SendMessage(
         to=worker,
         message={"type": "shutdown_request", "reason": "team disbanding -- workflow complete"}
       )
   ```
   Wait up to ~30s total for all `shutdown_response approve=true` messages. Their processes terminate on approve and they leave the team. Remove each confirmed shutdown from `spawned_workers`.

3. **One retry pass** for any worker that didn't approve within the window: re-send `shutdown_request`. If a worker still doesn't shut down after the retry, log its name and proceed -- don't loop indefinitely.

4. **Delete the team:**
   ```python
   TeamDelete(team_name=team_name)
   ```
   The team is gone. The user's team panel is clean.

**Why `shutdown_request` and not `TaskStop` or "you're released":** plain-text "you are released" tells the worker conceptually that work is done, but does NOT terminate their process. `TaskStop` doesn't work on teammate agents either. Only `shutdown_request` -> `shutdown_response approve=true` actually terminates the process and removes it from the team panel.

---

## Key Rules

**DO:**
- Standardize feature name to snake_case BEFORE sending to any agent
- Phase 1: ProductOwner is single, sequential, long-lived -- wait for explicit `APPROVED` before Phase 2
- Phase 2: Re-read the stories directory before every scheduling decision (YAML files are source of truth, not agent messages)
- Spawn per-story workers up to `max_parallel_workers`; never exceed the cap
- Use the role name + story id for worker names (`TestCreator-STORY-3`); add `-cN` suffix on re-cycles to keep names unique
- Tester workers always run **story-scoped** in this skill (`Test scope: story STORY-N`) so multiple Testers can run in parallel
- EpicVerifier workers run **epic-scoped**. They share the `max_parallel_workers` budget with story workers but typically only one or two will be in flight at a time (one per epic that's just reached DONE)
- After each worker sends its completion message, send `SendMessage(to=worker, message={"type": "shutdown_request", ...})` to actually terminate it. A plain-text "you are released" message does NOT remove the worker from the team panel -- only `shutdown_request` -> `shutdown_response approve=true` does. `TaskStop` doesn't work either. Track every spawned worker in `spawned_workers` and remove on confirmed shutdown.
- **Own the team lifecycle end-to-end** -- `TeamCreate` in Step 4, `shutdown_request` per worker on completion, `TeamDelete` in Step 9. Step 9 runs after every outcome (success / partial / timeout / deadlock). Never leave a team or its agents alive after the skill returns.
- **On `--resume`, force-clean the team first.** Call `TeamDelete(team_name=...)` (tolerating "not found") before the normal Step 4 TeamCreate. This guarantees orphan workers from a prior crashed run don't collide with fresh spawns or linger in the team panel. See Step 4 for the exact pattern.
- **Cycle budgets reset on resume.** A story that burned its `max_cycles` budget before interrupt gets a fresh budget after `--resume`. This is deliberate -- the user is implicitly granting permission to keep trying by invoking resume. If the story is genuinely unsolvable, intervene out-of-band; don't expect the orchestrator to remember.
- Track per-story cycle counts independently -- one stuck story doesn't drain the budget for others
- Forward ProductOwner's questions / approval requests to the User -- never answer or approve on their behalf
- Always invoke status flips through `.sage/_tools/update_story_status.py`; never edit story YAMLs directly. **The YAML is the event log.** Re-read it after every worker completion -- don't trust the message body alone, since the worker may report something that disagrees with what they actually wrote.
- Trust the Tester's two-gate verdict for DONE -- it runs **Gate A** (per-story tests pass, including no build/compile/dex errors) AND **Gate B** (`verify_ac_map.py` passes for the AC implementation map sidecar the Developer wrote at `STORY-N.implementation.md`). Don't override either gate. When a story comes back to `IN_DEV` because Gate A or Gate B failed, forward the failure details to the Developer in the next cycle's task message.
- **EpicVerifier is the third gate, scoped wider than per-story.** It runs only after every story in its epic is `DONE`. Trust its verdict the same way you trust the Tester's. On `FAILED`, it has already re-opened the specific stories that need fixing -- the next scan will pick them up at IN_DEV. On `VERIFIED`, it has written the verification artifact AND flipped the epic YAML to VERIFIED; this is what unblocks any downstream epics. Verifier failures count against the same `max_cycles` budget tracked in `verify_cycle_count[EPIC-N]`.
- Workers report ONE of three outcomes via their single completion message: `DONE` (success), `FAILED` (recoverable -- the YAML status is the previous one, e.g., Tester flipped TESTING->IN_DEV; next cycle handles it), or `BLOCKED` (unrecoverable -- requires user action). If a worker never sends a starting message or never sends a completion message within the timeouts, treat it as dead -- shutdown_request it and mark its story BLOCKED with reason `ack_timeout` or `work_timeout`.
- **Dependency satisfaction is mechanical, not judged.** Always call `list_eligible.py --feature <feature_name>` at the top of every scan and trust its bucketing. A story's deps are satisfied **only** when every dep's `status == "DONE"`. `TESTING` doesn't count. `IN_DEV` doesn't count. Don't second-guess the script. The script returning STORY-2 in `blocked_on_deps` means STORY-2 cannot be spawned this scan, regardless of how close its deps look to being done.
- Token recording is **automatic via discovery**: call `discover_and_record.py --feature <feature_name>` at exactly two trigger points -- once after PO approval (Step 5d), and once at the top of every scheduling scan (Step 6b). Don't try to record per-worker yourself; discovery walks the Claude Code transcripts directory and records anything missing. If discovery fails, log and continue -- never block the workflow on telemetry.

**DON'T:**
- Don't spawn an Orchestrator agent -- you ARE the orchestrator
- Don't pollute the shared task list with downstream-phase work. `TaskCreate`/`TaskUpdate` entries are visible to every spawned teammate (PO, TestCreator, Developer, Tester, EpicVerifier) via their TaskList view, and an entry like "Phase 2: spawn workers" or "EpicVerifier: verify EPIC-1" looks to a teammate like a task being assigned to them. If you must use TaskCreate for your own orchestrator notes, scope entries to the current phase only and delete them before moving on. Agents are instructed to treat the task list as informational, but the safest path is to not put confusing entries there in the first place. **The YAMLs are the state machine -- the task list is not.**
- Don't keep workers alive between stories -- spawn fresh per story so you can use natural shutdown for rate-limiting
- Don't run a Tester worker with `Test scope: full regression` from this skill -- full regression is for `/sage-dev-test` and the inline `/sage-tester --full`. The parallel scheduler relies on per-story scoping for safe concurrency.
- Don't preflight project-specific things (test commands, servers) -- that's each worker's job per its `.sage/` instructions
- Don't substitute variables in agent prompts -- the loader did that already

---

## References

- `.sage/HANDBOOK.md` -- Full protocol (completion reporting model, escalation, Monitor tool)
- `.sage/guides/ORCHESTRATOR_PATTERNS.md` -- Reusable Skill/Team Lead patterns
- `.sage/sage-config.SCHEMA.md` -- Config field reference (including `max_parallel_workers`, `global_timeout_seconds`)
- `.sage/_tools/update_story_status.py` -- Atomic, locked story-status updater used by all workers
- `.sage/_tools/verify_ac_map.py` -- Verifies a story's AC implementation map sidecar (Developer's mandatory artifact); Tester calls this as Gate B before flipping to DONE
- `.sage/_tools/discover_and_record.py` -- **Token-tracking entry point for this skill.** Walks `~/.claude/projects/<slug>/<session>/subagents/`, finds every worker transcript, records any not yet in the JSON store. Idempotent. Call once after PO approval and once per scheduling scan -- it handles everything else.
- `.sage/_tools/list_eligible.py` -- **Scheduling entry point for this skill.** Reads every story YAML (and every epic YAML if present), returns JSON with which stories are eligible for which role (TestCreator/Developer/Tester), which epics are ready to verify, and which stories are blocked on unmet deps (story-level OR epic-level). Call once at the top of every scheduling scan. Mechanical; treats `TESTING != DONE` and `DONE != VERIFIED` correctly.
- `.sage/_tools/verify_epic.py` -- **Precondition gate the EpicVerifier runs first.** Reads stories in scope and re-runs `verify_ac_map.py` for each. Surfaces non-DONE stories and stale AC maps. Doesn't run tests -- the verifier role does that.
- `.sage/_tools/update_epic_status.py` -- Atomic, locked epic-status updater. EpicVerifier calls this to flip `DONE -> VERIFIED` (or BLOCKED on unrecoverable cases).
- `.sage/_tools/rollup_status.py` -- Read-only renderer; produces a human-readable per-epic / per-story rollup view of feature progress from the authoritative YAMLs. Call once at the end of Phase 2 (Step 7) with `--write` to refresh `progress.md`.
- `.sage/_tools/prepare_task_payload.py` -- Renders the spec + one or more story YAMLs + optional epic YAML as a markdown block suitable for embedding in a worker's task message. Call once per task spawn (Step 6d) and paste stdout into the SendMessage body. This eliminates the bootstrap `Read` tool calls a fresh worker would otherwise make, trimming cache_create on its first few turns.
- `.sage/_tools/extract_token_usage.py` -- Used internally by `record_worker_usage.py` to parse one transcript's usage. Don't call from this skill.
- `.sage/_tools/record_worker_usage.py` -- Used internally by `discover_and_record.py` to record one worker. Also called directly from this skill in Step 7 to record the orchestrator's own main-session diff.
- `.sage/` -- Reference per-agent instruction configs
