---
name: sage-dev-test
description: Ad hoc dev/test cycle -- runs existing tests and fixes failures iteratively
when_to_use: When you want to run tests and fix failures without a full feature workflow (no spec, no progress file, full regression by default)
---

# Sage Dev/Test Skill

You are the **Team Lead** for an ad-hoc dev/test cycle. You coordinate two agents -- **Developer** (fixes code) and **Tester** (runs tests) -- until tests pass or `max_cycles` is hit.

> **Path note:** The `python .sage/_tools/load_agents.py` command below assumes you're in the sage-feature-team source repo. From inside an installed project (a `.sage/` directory exists), use `python .sage/_tools/load_agents.py` instead. Agent prompts spawned from the loader already have helper-script paths baked in by `load_agents.py` via `{SAGE_TOOLS_DIR}`.

This is a **stateless** workflow:
- No feature spec
- No progress file
- No `output_dir` writes
- Just iterate until green or escalate

The actual test command, test paths, and result parsing all come from the **Tester's** `.sage/sage-tester-config.yaml` instructions -- not from this skill.

---

## Step 1: Parse Input

```bash
/sage-dev-test                          # Full regression (default)
/sage-dev-test test_login test_register # Targeted tests
/sage-dev-test --max-cycles 10          # Override cycle limit
```

Compute these once and reuse:
- **targeted_tests** -- list from positional args, or empty (full regression)
- **test_scope** -- `"full regression"` if no targeted tests, else `"specific tests: <names>"`
- **max_cycles** -- from `--max-cycles N` if given, else from config

Don't ask the user for a feature name or requirements. Use the literal label `dev_test_cycle` in messages.

When you send messages to agents below, write the message naturally with these literal values inlined -- not Python f-string syntax with `{...}` placeholders.

---

---

## Step 2: Load Agent Prompts

```bash
python .sage/_tools/load_agents.py dev-test-only
```

Expected JSON:

```json
{
  "success": true,
  "mode": "dev-test-only",
  "team_name": "<from sage-config.yaml team.dev_test_team_name>",
  "agent_names": ["Developer", "Tester"],
  "agents": {
    "Developer": "<fully rendered prompt>",
    "Tester":    "<fully rendered prompt>"
  },
  "sage_dir": "<path to .sage/ directory>",
  "config_summary": { "project_name": "...", "absolute_root_dir": "..." }
}
```

Validate: `success == true`, `agent_names == ["Developer", "Tester"]`, both in `agents`. If `sage_dir` is null, the agents will get a "no project instructions configured" placeholder -- warn and continue.

---

## Step 3: Create Team and Spawn Agents

```python
TeamCreate(team_name=team_name, description="Sage dev/test cycle team")

Agent(name="Developer", prompt=agents["Developer"], team_name=team_name, subagent_type="general-purpose")
# wait 2-3s
Agent(name="Tester",    prompt=agents["Tester"],    team_name=team_name, subagent_type="general-purpose")
```

Both agents idle until you send a SendMessage task -- `_BASE.md` enforces the Task-Waiting Rule.

---

## Step 4: Initial Test Run (Discovery)

Send a SendMessage to `Tester` whose body includes:
- `@User: [Dev-Test] Run tests and report results. (Initial discovery)` opener
- `[Task: tester-discovery]`
- `Test scope: <test_scope>` (literal -- `"full regression"` or the targeted test names)
- `Reference: HANDBOOK.md`

Run **starting-message + completion monitoring** (Step 6).

Parse Tester's completion message:
- Extract `TEST_FAILURE: <test_name> | expected=<x> | actual=<y> | error=<msg>` lines
- Count passed and failed

If all passed -> jump to **Step 7 (Success Report)**. Otherwise -> **Step 5 (Cycle Loop)**.

---

## Step 5: Dev/Test Cycle Loop

```
cycle_count = 1
max_cycles  = (from --max-cycles or config)

WHILE cycle_count <= max_cycles:
  [1] Send Developer task with current failing test names + previous failure details
      Run starting-message + completion monitoring
  [2] Send Tester task (same scope as discovery)
      Run starting-message + completion monitoring
      Parse TEST_FAILURE lines
  [3] If all PASSED -> break (success)
      If FAILED and cycle_count < max -> cycle_count++, loop
      If FAILED and cycle_count == max -> escalate "Max cycles exceeded"
```

### Developer message

Send to `Developer`, body includes:
- `@User: [Dev-Test Cycle <n>/<max>] Make failing tests pass.` opener
- `[Task: dev-cycle-<n>] [Cycle: <n>/<max>]`
- If cycle > 1: paste previous cycle's `TEST_FAILURE` lines verbatim
- `Failing tests to fix:` followed by a bulleted list of test names
- `Reference: HANDBOOK.md`

### Tester message

Send to `Tester`, body includes:
- `@User: [Dev-Test Cycle <n>/<max>] Run tests and report results.` opener
- `[Task: tester-<n>] [Cycle: <n>/<max>]`
- `Test scope: <test_scope>` (same value as discovery)
- `Reference: HANDBOOK.md`

### Idle = completion

When an agent sends `STATUS: DONE | READY: yes` (or `STATUS: FAILED`) and goes idle, that idle notification IS the completion signal. Read the agent's last message and route immediately.

---

## Step 6: Monitoring (Starting Message + Completion)

Same as `/sage-feature-team` -- see that skill's "Step 8: Monitoring" section. Briefly:

| Phase | Deadline | On miss |
|---|---|---|
| Starting message | 60s | Escalate -- send `shutdown_request`, treat agent as dead, abort cycle |
| Completion | 8 min (work timeout) | Escalate -- send `shutdown_request`, treat as deadlocked, abort cycle |

No SYN/SYN-ACK/ACK handshake. No message-ID dedup. The agent sends one starting message and one completion message; you re-read story state (or test output) to route. Send `shutdown_request` after each completion to clean up the worker.

---

## Step 7: Final Report

### Success

```
@User: [Dev-Test] Test/Fix Cycle Complete

Status: SUCCESS
Cycles used: {cycle_count}/{max_cycles}

All tests passing. See git diff for code changes.
```

### Escalation

```
@User: [Dev-Test] Workflow Blocked

Status: ESCALATION
Blocker: <starting-message timeout | Work timeout | Max cycles exceeded | Test hang>

Details: <list failing tests, last error, agent that hung>
Recommended action: <what the user should do>
```

---

## Key Rules

**DO:**
- Assume dev-test-only mode (Developer + Tester only -- no ProductOwner, no TestCreator)
- Default to full regression unless `/sage-dev-test <test_names>` was given
- Send the first task to **Tester** (initial discovery), then alternate Developer -> Tester per cycle
- Trust the Tester's `.sage/` instructions for the test command -- don't hardcode one here
- Treat the agent's single completion message as authoritative -- no SYN-ACK reply, no message-ID tracking, no dedup

**DON'T:**
- Don't ask the user for a feature name or requirements
- Don't create or read a progress file (this workflow is stateless)
- Don't preflight the test command or runner -- Tester validates that per its project instructions
- Don't make technical decisions -- escalate to User if anything is ambiguous

---

## References

- `.sage/HANDBOOK.md` -- Full protocol (completion reporting model, escalation, Monitor)
- `.sage/guides/ORCHESTRATOR_PATTERNS.md` -- Reusable patterns shared with `sage-feature-team`
- `.sage/agents/developer.md`, `.sage/agents/tester.md` -- Agent role files
- `.sage/sage-tester-config.yaml` -- Reference Tester config


---

## Token Tracking (Record)

After reporting to the user, record this skill's estimated token consumption:

```bash
python .sage/_tools/record_worker_usage.py     --feature <feature_name> --role Developer --story - --cycle 1     --inline --output-chars <approximate output chars produced>
```

Inline-mode entries are flagged `estimated: true` in `_output/<feature_name>/tokens.json` because we can't measure exact tokens from inside the main conversation (use `/usage` for the precise session total). Estimate `output-chars` as roughly the size of files you wrote + your final user-facing report. Failure here is non-fatal -- log and continue.
