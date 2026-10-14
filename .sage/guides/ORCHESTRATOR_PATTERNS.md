# Orchestrator Patterns (Reusable)

**Shared patterns used by both `/sage-feature-team` and `/sage-dev-test` skills.**

These patterns can be referenced by any skill that spawns and coordinates agents. Extract them here to avoid duplication across skill definitions.

---

## Pattern 1: Starting-Message Deadline

**Used by:** Both skills for every task they send to a worker

**Purpose:** Detect dead workers fast. One deadline -- no graduated nudges.

**Timeline:**
- **T=0-60s:** Wait for the worker's `Starting on STORY-N` SendMessage
- **T=60s exactly:** If no starting message has arrived, the worker is treated as dead. Send `shutdown_request`, mark its story BLOCKED with `reason=ack_timeout`, remove from `spawned_workers` on confirmed shutdown, continue scheduling.

**Values from config:**
- `timeout_starting_message: 60`

**Implementation:**
```python
send_task_to_worker(worker_name, task_message)

if no_starting_message_after(60s):
    SendMessage(
      to=worker_name,
      message={"type": "shutdown_request", "reason": "ack_timeout"}
    )
    update_story_status.py STORY-N BLOCKED --reason "ack_timeout"
    spawned_workers.discard(worker_name_once_shutdown_confirmed)
    continue_scheduling()
else:
    continue_to_work_completion_monitoring()
```

There is **no** 30s/45s gentle-nudge sequence. The whole point of dropping the old handshake protocol is to stop spending latency on retransmission for a problem (packet loss between Claude agents) that doesn't exist. One deadline; one consequence.

---

## Pattern 2: Work Completion Monitoring

**Used by:** Both skills after the starting message has arrived

**Purpose:** Wait for the worker's single completion message; detect deadlocks.

**Timeline:**
- **0 - `timeout_work_hard`/2:** Silent wait. The worker is doing real work; don't poke.
- **`timeout_work_hard`/2 (optional):** Send a gentle status check ("How's it going?") if you want a heartbeat. The worker can answer if reachable.
- **`timeout_work_hard` (hard):** Escalate. Send `shutdown_request` with `reason=work_timeout`, mark story BLOCKED, continue.

**Values from config:**
- `timeout_work_hard: 480` (8 minutes)

**Implementation:**
```python
# After starting message received
start_work_timer = now()

if no_completion_after(timeout_work_hard / 2):
    send_status_check()   # optional; non-blocking

if no_completion_after(timeout_work_hard):
    SendMessage(
      to=worker_name,
      message={"type": "shutdown_request", "reason": "work_timeout"}
    )
    update_story_status.py STORY-N BLOCKED --reason "work_timeout"
    continue_scheduling()
else:
    # Completion message arrived
    re_read_story_yaml()       # source of truth -- the worker already flipped it
    route_based_on_new_status()
    SendMessage(to=worker_name, message={"type": "shutdown_request", ...})
```

---

## Pattern 3: Dev/Tester Cycle Loop

**Used by:** Both skills for test/fix cycles

**Purpose:** Iterate Developer<->Tester until tests pass or `max_cycles` reached.

```
cycle_count = 1
max_cycles  = (from config)

WHILE cycle_count <= max_cycles:

  [1] Spawn / route Developer for the story:
      Send task
      Wait for starting message (Pattern 1)
      Wait for completion message (Pattern 2)
      Re-read story YAML: confirm status is now TESTING

  [2] Spawn / route Tester for the story:
      Send task (story-scoped)
      Wait for starting message (Pattern 1)
      Wait for completion message (Pattern 2)
      Re-read story YAML: check new status

  [3] Decide:
      IF status == DONE:
        SUCCESS, break
      ELSE IF status == IN_DEV AND cycle_count < max_cycles:
        Extract failure details from Tester's completion message
        cycle_count += 1
        LOOP
      ELSE IF cycle_count == max_cycles AND status == IN_DEV:
        ESCALATE "Max cycles exceeded"; break
      ELSE IF status == BLOCKED:
        ESCALATE with Tester's blocker reason; break

END WHILE
```

The orchestrator routes off the **YAML status**, never the message body. The message gives context; the YAML is truth.

---

## Pattern 4: SendMessage Format Standard

**Used by:** Both skills for all agent communications

**Format:**
```python
SendMessage(
  to="WorkerName",
  summary="[Feature: feature_name] Brief action description",
  message="""@User: [Feature: feature_name] Detailed instructions.

[Task: task-id-feature-name]

Context:
[Relevant files, background information]

Job: [What worker should do]

Reference: HANDBOOK.md""")
```

**Key Elements:**
- `to`: Worker name (e.g., `Developer-STORY-3`, `Tester-STORY-1`)
- `summary`: One-line action (under 70 characters)
- `message`: Multiline with `@User` prefix, task ID, context, job, reference
- Feature context: Always include `[Feature: feature_name]` for tracing
- Reference: Point to `HANDBOOK.md` for protocol details

---

## Pattern 5: Timeout & Escalation Templates

**Used by:** Both skills when timeouts occur

### Starting-Message Timeout (worker didn't send "Starting" within 60s)

```python
SendMessage(
  to="User",
  summary="Feature workflow: STORY-N timeout",
  message="""[Feature: {feature_name}] STORY-N marked BLOCKED -- starting-message timeout

Worker: {worker_name}
Time: Waited 60s for "Starting on STORY-N" SendMessage; none arrived.
Phase: {phase}

Action taken: shutdown_request sent, story flipped to BLOCKED (reason=ack_timeout). Scheduler continues other stories.
Recommended action: Inspect the agent transcript; if it's a recurring issue, raise `timeout_starting_message` or investigate why the agent never spawned.""")
```

### Work Timeout (worker stopped after sending Starting but never sent Completion)

```python
SendMessage(
  to="User",
  summary="Feature workflow: STORY-N work timeout",
  message="""[Feature: {feature_name}] STORY-N marked BLOCKED -- work timeout

Worker: {worker_name}
Time: Worker sent its starting message but went silent for {timeout_work_hard}s.
Phase: {phase}

Action taken: shutdown_request sent, story flipped to BLOCKED (reason=work_timeout). Scheduler continues other stories.
Recommended action: Inspect the agent transcript; common causes include hung Monitor tool, infinite loop in test parsing, or missed ScheduleWakeup chain.""")
```

### Cycle Limit Exceeded

```python
SendMessage(
  to="User",
  summary="Feature workflow: STORY-N max cycles",
  message="""[Feature: {feature_name}] STORY-N marked BLOCKED -- max_cycles exceeded

Cycles used: {cycle_count}/{max_cycles}
Last failure: {extracted from Tester's last completion message}

The Developer<->Tester loop did not converge within max_cycles. Review the failure (likely a test that's underspec'd, a missing dependency, or an AC that can't be implemented at the available seam) and either fix the test, the spec, or raise max_cycles.""")
```

---

## Pattern 6: Final Reporting

**Used by:** Both skills when workflow completes

### Success (Full Mode, all stories DONE)

```python
SendMessage(
  to="User",
  summary="Feature complete: {feature_name}",
  message="""[Feature: {feature_name}] Feature Development Complete

Status: SUCCESS
Mode: Full Workflow
Stories: {N} DONE
Cycles used (per story): {map}

Artifacts:
- Spec:        _output/{feature_name}/spec.md
- Stories dir: _output/{feature_name}/stories/
- Tests:       see git diff
- Code:        see git diff

All acceptance criteria tested and passing.""")
```

### Success (Dev-Test Mode)

```python
SendMessage(
  to="User",
  summary="Tests fixed: {feature_name}",
  message="""[Feature: {feature_name}] Test/Fix Cycle Complete

Status: SUCCESS
Mode: Dev-Test Only
Cycles used: {cycle_count}/{max_cycles}

Test Results: all passing
Code changes: see git diff""")
```

### Escalation

```python
SendMessage(
  to="User",
  summary="Feature workflow blocked: {reason}",
  message="""[Feature: {feature_name}] Feature Development Partially Complete

Status: PARTIAL or ESCALATION
Stories DONE: {list}
Stories BLOCKED: {list with reasons}

Resolve blocked stories out-of-band (edit spec, fix config, etc.) and re-run with --resume.""")
```

---

## How Skills Use These Patterns

### /sage-feature-team Skill

1. **Phase 1 (full mode):** ProductOwner
   - Pattern 1 (Starting-Message Deadline)
   - Pattern 2 (Work Completion)
   - Pattern 4 (SendMessage Format)
   - Pattern 5 (Escalation Templates) on timeouts

2. **Phase 2 (full mode):** Parallel scheduler over per-story workers
   - Pattern 1, 2, 4 for every worker spawn
   - Pattern 3 (Dev/Tester Cycle Loop) per story
   - Pattern 5 on per-story timeouts / max-cycles
   - Pattern 6 (Final Reporting) when the scheduler drains

### /sage-dev-test Skill

1. **Mode:** dev-test-only (tests already exist; no PO / TestCreator)
2. **Loop:** Pattern 3 directly, with Patterns 1, 2, 4, 5 for each cycle step
3. **Wrap-up:** Pattern 6

### Pattern Reuse Summary

| Pattern | sage-feature-team | sage-dev-test | Notes |
|---|---|---|---|
| 1: Starting-Message Deadline | yes (every worker) | yes (every worker) | One 60s deadline; no graduated probes |
| 2: Work Completion | yes (every worker) | yes (every worker) | Soft check optional; hard timeout fires shutdown |
| 3: Cycle Loop | yes (per story) | yes (single story scope) | Routes off YAML status, not message body |
| 4: SendMessage Format | yes | yes | Identical |
| 5: Escalation Templates | yes | yes | Identical |
| 6: Final Reporting | yes | yes | Mode-specific copy |

---

## Configuration Values Used by Patterns

From `sage-config.yaml`:

```yaml
limits:
  max_cycles: 5                  # Pattern 3: max dev/test cycles per story
  timeout_starting_message: 60   # Pattern 1: single deadline for the "Starting on STORY-N" SendMessage
  timeout_work_hard: 480         # Pattern 2: hard timeout per work step (8 min)
```

---

**Status:** Ready for reference by both skills.
