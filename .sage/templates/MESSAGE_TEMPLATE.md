# Unified Message Template (Simple)

**One template for all worker messages.** Two messages per task: one starting message, one completion message.

---

## Basic Structure

```
@User: [Feature: {name}] {subject}

[Task: {id}] [Cycle: {n}/{m}]

{message content}

--- STATUS: {status} | READY: {yes/no} | BLOCKER: {none/reason}
```

The STATUS/READY/BLOCKER footer applies to **completion** messages. The starting message is a one-liner and doesn't need it.

---

## Three Key Fields (Completion Messages)

| Field | Values | Purpose |
|-------|--------|---------|
| **STATUS** | `DONE`, `FAILED`, `BLOCKED` | Outcome of the task |
| **READY** | `yes` or `no` | Is the worker idle and ready for `shutdown_request`? |
| **BLOCKER** | `none` or brief category (e.g., `tagging_convention_undefined`, `infra_unreachable`) | Required when `STATUS: BLOCKED` |

**STATUS values map to the three completion outcomes in `agents/_BASE.md`:**
- `DONE` -- success; story status advanced to its next state (e.g., `TESTING`, `DONE`)
- `FAILED` -- recoverable; story flipped back to previous state (e.g., `IN_DEV`); next cycle handles it
- `BLOCKED` -- unrecoverable; story flipped to `BLOCKED`; user must intervene

---

## Examples

### Orchestrator -> Developer: Task Assignment
```
@User: [Feature: auth_system] Fix failing tests. (Cycle 2/5)

[Task: dev-STORY-3-c2-auth_system] [Cycle: 2/5]

Failing tests from Cycle 1:
- test_login: Expected 200, got 401
- test_register: Expected 201, got 500

Target story:  STORY-3
Spec:          _output/auth_system/spec.md
Stories dir:   _output/auth_system/stories/
Progress file: _output/auth_system/progress.md

Reference: HANDBOOK.md
```

### Worker -> Orchestrator: Starting Message (within 60s of receipt)
```
@User: [Feature: auth_system] Starting on STORY-3 (Cycle 2/5).
```

That's it. One line. No STATUS footer, no protocol markers.

### Worker -> Orchestrator: Completion (DONE)
```
@User: [Feature: auth_system] Code complete.

[Task: dev-STORY-3-c2-auth_system]

Fixed:
- test_login (changed hash algorithm back to bcrypt)
- test_register (fixed validation logic)

Files: sage/auth/login.py, sage/auth/validators.py
Stories advanced to TESTING: STORY-3
AC map sidecar: _output/auth_system/stories/STORY-3.implementation.md (passed verify_ac_map.py)

--- STATUS: DONE | READY: yes | BLOCKER: none
```

### Worker -> Orchestrator: Completion (FAILED -- recoverable)
```
@User: [Feature: auth_system] Tests failed.

[Task: tester-STORY-3-c2-auth_system]

Results: 8 passed, 2 failed in 14.2s.

{"test_results": {"passed": 8, "failed": 2, "failures": [
  {"test": "test_login", "expected": 200, "actual": 401, "error": "Unauthorized"},
  {"test": "test_register", "expected": 201, "actual": 500, "error": "DB constraint"}
]}}

Stories sent back to IN_DEV (had failing tests): STORY-3

--- STATUS: FAILED | READY: no | BLOCKER: none
```

### Worker -> Orchestrator: Completion (BLOCKED -- truly unrecoverable)
```
@User: [Feature: auth_system] BLOCKED on STORY-3.

[Task: tester-STORY-3-c2-auth_system]

STATUS: BLOCKED | BLOCKER: tagging_convention_undefined

Why: .sage/sage-tester-config.yaml doesn't define how tests are tagged for story scope. I cannot determine which tests belong to STORY-3 without that convention.
What user must decide: Add a tagging-convention entry to sage-tester-config.yaml (e.g., pytest marker, JUnit @Tag, naming prefix).
Current state: Tests exist at tests/test_auth_system.py but are not tagged.
Recommended action: Edit sage-tester-config.yaml to declare the convention, then re-run with --resume.

--- STATUS: BLOCKED | READY: no | BLOCKER: tagging_convention_undefined
```

---

## When to Include Each Field

| Field | Always? | Usage |
|-------|---------|-------|
| `@User:` | YES | Every message |
| `[Feature: X]` | YES | Every message |
| `{subject}` | YES | One-line summary |
| `[Task: id]` | YES on task assignments + completion messages | Links completion back to task |
| `[Cycle: n/m]` | Only in dev/test cycles | Which cycle is this |
| STATUS / READY / BLOCKER | YES on completion messages, NOT on starting message |  |

---

## Parsing (For Orchestrator)

```python
import re

feature = re.search(r"\[Feature: ([^\]]+)\]", msg).group(1)
task_id = re.search(r"\[Task: ([^\]]+)\]", msg)
task_id = task_id.group(1) if task_id else None

# Completion footer (only on completion messages)
status_m  = re.search(r"STATUS: (\w+)", msg)
ready_m   = re.search(r"READY: (\w+)", msg)
blocker_m = re.search(r"BLOCKER: ([^\|\n]+)", msg)

status  = status_m.group(1)  if status_m  else None
ready   = ready_m.group(1)   if ready_m   else None
blocker = blocker_m.group(1).strip() if blocker_m else None
```

For routing decisions, **don't rely on the parsed status -- re-read the story YAML instead.** The YAML is the source of truth; the message provides human-readable detail.

---

## Validation Checklist

**Starting message:**
- [ ] `@User:` prefix present
- [ ] `[Feature: name]` present and correct
- [ ] Mentions the story or task being started
- [ ] One line, no STATUS footer

**Completion message:**
- [ ] `@User:` prefix present
- [ ] `[Feature: name]` present and correct
- [ ] `[Task: id]` present
- [ ] Body includes the role-specific payload (test results, files changed, blocker reason, etc.)
- [ ] STATUS is one of `DONE`, `FAILED`, `BLOCKED`
- [ ] READY is `yes` (DONE) or `no` (FAILED/BLOCKED)
- [ ] BLOCKER is `none` (DONE/FAILED) or a category (BLOCKED)
- [ ] Story YAML status was flipped via `update_story_status.py` *before* sending this message
