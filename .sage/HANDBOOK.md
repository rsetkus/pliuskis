# Agent Protocol Handbook

**Single source of truth for all agent communication patterns**

This document is referenced by all 4 agents (ProductOwner, TestCreator, Developer, Tester) and eliminates duplication across instruction files. The skill (running as team lead) coordinates routing and escalation.

---

## Table of Contents

1. [Completion Reporting Model](#completion-reporting-model)
2. [Stop on Questions - Escalate Pattern](#stop-on-questions---escalate-pattern-all-agents)
3. [SendMessage Format Standard](#sendmessage-format-standard-all-team-communications)
4. [Unified Reporting Patterns](#unified-reporting-patterns-all-agents)
5. [Feature Context Rules](#feature-context-rules)
6. [Unified Timeout & Escalation](#unified-timeout--escalation-reference-table)
7. [Workflow Routing](#workflow-routing-orchestrator-decision-tree)
8. [Monitor Tool](#monitor-tool-for-long-running-tasks-tester--others)
9. [Progress File Updates](#progress-file-updates-mandatory---all-agents)
10. [Timestamp Logging](#timestamp-logging-all-agents---team-mode)
11. [Roles Overview](#feature-development-workflow-roles-at-a-glance)
12. [Team vs Solo Mode](#team-mode-vs-solo-mode)
13. [Quick Start Checklist](#before-you-start-work-checklist)
14. [Protocol Summary](#summary-the-protocol-in-one-page)

---

## Completion Reporting Model

**The story YAML is the event log.** The orchestrator reconciles by re-reading it after every worker completion. Communication is two messages, both via `SendMessage`:

1. **"Starting" message** -- within 60s of task receipt. Confirms the worker is alive and has the task.
2. **Completion message** -- exactly one, when the worker is done. Carries the outcome payload (DONE / FAILED / BLOCKED) for human-readable detail.

Between them the worker is silent. After the completion message, the worker accepts the orchestrator's `shutdown_request` and terminates.

There is **no SYN/SYN-ACK/ACK three-way handshake**, no message-ID dedup, no retransmission machinery. State authority lives in the YAML (locked, atomic) via `update_story_status.py`. The completion message is for visibility, not for transport reliability.

### Worker Side

```
Receive SendMessage task
  v Send "Starting on STORY-N" within 60s (one message, no retry)
  v Do work (silently)
  v Flip story status via update_story_status.py (atomic + locked)
  v Send completion message (one message, payload per role file)
  v Accept shutdown_request -> approve=true -> terminate
```

### Orchestrator Side

```
Spawn worker, send task
  v If "Starting" message doesn't arrive within 60s: assume dead, mark its story BLOCKED with reason=ack_timeout, send shutdown_request, continue scheduling
  v Otherwise wait for completion message
  v On completion: re-read story YAML (the source of truth), route based on new status
  v Send shutdown_request, confirm shutdown_response approve=true
```

### Starting Message Format

**Team mode:**
```
@User: [Feature: feature_name] Starting on STORY-N (or task-id).
```

**Solo mode:** plain text reply is fine; no SendMessage needed.

Do NOT begin substantive work before sending this. It costs ~one second and tells the orchestrator the worker is alive.

---

## Stop on Questions - Escalate Pattern (All Agents)

**If you encounter ANY question, ambiguity, or uncertainty:**

### Action Sequence
1. **STOP immediately** -- do not make assumptions or guesses
2. **Escalate to User** via SendMessage (team mode only)
3. **Wait for response** -- do not proceed until you have answer
4. **Use answer** -- incorporate guidance into your work

### Escalation Format (Team Mode)
```
@User: [Feature: name] I need clarification before continuing.

Question: [Your specific question]
Context: [Why this matters for your work]
Current interpretation: [What you're assuming]
Options: [If applicable, possible interpretations]

Waiting for user input before proceeding.
```

### Direct Format (Solo Mode)
```
I need clarification before proceeding:

Question: [Your specific question]
Context: [Why this matters]
Options: [If applicable, possible interpretations]

Please advise how to proceed.
```

### Key Rules
- [RULE] Always escalate to User when in team mode; never guess or assume
- [RULE] Never pick "most likely" interpretation without confirmation
- [RULE] Never proceed with assumptions
- [RULE] Escalate early, don't waste time guessing

---

## SendMessage Format Standard (All Team Communications)

**All messages use the unified template** (see `templates/MESSAGE_TEMPLATE.md`):

```
@User: [Feature: name] {subject}

[Task: id] [Cycle: n/m]  (optional, include when applicable)

{message body}

--- STATUS: {status} | READY: {yes/no} | BLOCKER: {none/reason}
```

**Critical rules:**
- Always include `@User:` prefix and `[Feature: name]`
- Always use SendMessage tool (not text output)
- Always include STATUS, READY, and BLOCKER fields at end
- For task assignments: include `[Task: id]` for sequencing
- For cycle work: include `[Cycle: n/m]` context

**Examples:** See `templates/MESSAGE_TEMPLATE.md` for all message types (starting, completion, escalation, cycle summary, etc.)

---

## Test Failure Reporting Format (Structured JSON)

**Critical for cycle context injection:** When Tester reports test failures, use JSON format so Skill can reliably parse and inject results into Developer's next cycle.

### Format (Required)

Include this JSON object in your completion message:

```json
{
  "test_results": {
    "passed": 12,
    "failed": 3,
    "failures": [
      {
        "test": "test_login_valid_email",
        "expected": 200,
        "actual": 401,
        "error": "Unauthorized"
      },
      {
        "test": "test_register_new_user",
        "expected": 201,
        "actual": 500,
        "error": "Database constraint violation"
      },
      {
        "test": "test_password_reset",
        "expected": 200,
        "actual": 404,
        "error": "endpoint not found"
      }
    ]
  }
}
```

### How Skill Uses This

Skill parses JSON and injects into Developer's next cycle:

```python
# Skill's extraction (JSON parsing)
test_data = json.loads(tester_message)
failures = test_data["test_results"]["failures"]

# Developer receives in next cycle:
Previous cycle failures:
- test_login_valid_email: Expected 200, got 401 (Unauthorized)
- test_register_new_user: Expected 201, got 500 (Database constraint violation)
```

### Important Rules

- [RULE] Include JSON object for EVERY test run (even if all pass, use empty failures array)
- [RULE] Use exact field names (test, expected, actual, error)
- [RULE] `error` can be multi-word string
- [RULE] Include in the completion message payload
- [RULE] If actual/expected unknown, use null: `"expected": null, "actual": null`

---

## Feature Context Rules

### What is Feature Context?

Messages must always include feature name in brackets: `[Feature: auth_system]`

This maintains traceability when multiple features are in development.

### Extraction Rule

When you receive a message like:
```
@ProductOwner: [Feature: auth_system] Create specification from REQUIREMENTS.md
```

Extract the feature name: **auth_system**

Use it in all file paths and references:
- Spec file: `_output/auth_system/spec.md`
- Progress file: `_output/auth_system/progress.md`
- Test file: `tests/test_auth_system.py`

### Echo in Responses

Always echo the feature context in your responses:
```
@User: [Feature: auth_system] Specification complete...
```

This confirms you worked on the correct feature.

---

## Unified Timeout & Escalation Reference Table

**Single source of truth for all timeout rules**

| Scenario | Event | Timeout | Action |
|----------|-------|---------|--------|
| **Starting Message** | Worker confirms task received | 60s | If absent: mark story BLOCKED (reason=ack_timeout), send shutdown_request, continue scheduling |
| **Agent Work** | Worker should report completion | 5 min soft | Ask for status; escalate at 8 min |
| **Test Output** | Tester monitoring tests | 30s no output | Immediate hang report |
| | | 15 min absolute | Hard timeout -- kill & report |
| **Spec Review** | ProductOwner awaits user feedback | 5 min | Ask for status; escalate after |
| **Same Test Fails** | Developer fixes, re-run fails same test | Cycle N+1 | 2nd fix attempt -- escalate after 2nd fail |
| **Workflow Duration** | Entire feature development | 25 min warn | Escalate at 30 min |

The orchestrator does **not** retry the starting-message check or send graduated nudges. The 60-second window is a single deadline. If it's missed, the worker is treated as dead -- mark its story BLOCKED with `ack_timeout`, shut it down, and continue. No graduated 30s/45s/60s probing.

---

## Workflow Routing (Skill/Team Lead Decision Tree)

### State-Driven Agent Routing

The skill reads progress file and routes based on current story statuses:

```
1. ProductOwner = PENDING -> Route to ProductOwner
2. TestCreator = PENDING AND ProductOwner = COMPLETE -> Route to TestCreator
3. Developer = PENDING AND TestCreator = COMPLETE -> Route to Developer
4. Tester = PENDING AND Developer = COMPLETE -> Route to Tester
5. Tester = FAILED -> Route to Developer to fix
6. ALL = COMPLETE -> Feature is COMPLETE
```

### Routing Logic

Each phase completes before the next phase begins:

| Current State | Next Action | Route To |
|---------------|------------|----------|
| All PENDING | Need specification | ProductOwner |
| ProductOwner DONE, Tests PENDING | Need test code | TestCreator |
| Tests DONE, Development PENDING | Need implementation | Developer |
| Development DONE, Testing PENDING | Need validation | Tester |
| Testing PASSED | All work complete | [Feature Complete] |
| Testing FAILED | Fix implementation | Developer |

### Reading Progress File

Before routing:
1. Read `_output/[name]/progress.md`
2. Find the first incomplete phase
3. Route to appropriate agent
4. Do NOT skip phases

**Critical: Dev/Test Cycle Ordering**
- When entering the dev/test cycle (after TestCreator completes), always route to Developer FIRST
- Developer fixes code; Tester validates it
- Never route to Tester before Developer has completed (even if tests exist)
- Breaking this rule causes tests to fail (Tester has nothing to validate, Developer never gets a chance to fix)

---

## Error Escalation Protocol

### What Requires Escalation?

**Always escalate (stop work immediately):**
- Ambiguity in requirements or spec
- Question about implementation approach
- Test hangs (no progress for 30+ seconds)
- Same test failing in consecutive cycles
- Blocked by external dependency
- Unable to proceed after 2 fix attempts
- Any situation where guessing could cause wasted effort

### Escalation Message Format

```
@User: [Feature: name] ESCALATION: [Issue Title]

What was being attempted:
[Describe the work you were doing]

Where it got stuck:
[Describe the blocker or ambiguity]

What errors/symptoms occurred:
[Include error messages, stack traces, failure details]

Investigation done so far:
[What have you tried? What have you ruled out?]

Recommended next steps:
[What do you think should happen next?]

Status: Blocked, awaiting user guidance
```

### Escalation Triggers by Agent

**ProductOwner:**
- Spec requirements unclear or contradictory
- User wants architectural changes mid-spec
- Need clarification on scope/phasing

**TestCreator:**
- Spec ambiguity makes tests unclear
- Unsure what behavior to test
- Need guidance on test approach

**Developer:**
- Spec ambiguity on implementation approach
- Same test fails in 2nd consecutive fix attempt
- Blocked by dependencies or architecture

**Tester:**
- Test hangs (30+ seconds no output)
- Test failure unclear how to fix
- Uncertainty about what to test

**Skill (Team Lead):**
- Worker doesn't send a Starting message within 60s of task receipt -> mark story BLOCKED (reason=ack_timeout), shutdown_request, continue scheduling other stories
- Worker doesn't send a completion message within the work-timeout window (8 min default) -> treat as deadlocked, shutdown_request, mark story BLOCKED with reason=work_timeout
- Workflow running >30 minutes -> warn user
- Multiple test hang attempts unsuccessful

---




## Unified Reporting Patterns (All Agents)

**All reports use the unified template** (see `templates/MESSAGE_TEMPLATE.md`):

Two report types (one of each per task):

1. **STARTING** -> `@User: [Feature: name] Starting on STORY-N (or task-id).` (send within 60s)
2. **COMPLETION** -> one of:
   - `STATUS: DONE | READY: yes` -- success
   - `STATUS: FAILED | READY: no` -- recoverable; next worker re-cycles
   - `STATUS: BLOCKED | READY: no | BLOCKER: reason` -- user must decide

**Key rules:**
- Always include `@User:`, `[Feature: name]`, and STATUS/READY/BLOCKER fields on completion
- Use SendMessage tool, never text output alone
- Flip the story YAML status via `update_story_status.py` **before** sending the completion message -- the YAML is the orchestrator's source of truth
- List artifacts clearly (files changed, tests created, etc.)
- **Tester ONLY:** Include TEST_FAILURE lines for all failures (see "Test Failure Reporting Format" section)

**Examples:** See `templates/MESSAGE_TEMPLATE.md` for each agent's completion format

---

## Monitor Tool for Long-Running Tasks (Tester & Others)

### Overview: Responsive Execution Pattern

For long-running tasks (test execution, complex operations), **use the Monitor tool** to stay responsive to user queries while work proceeds in the background.

### Problem It Solves

**Without Monitor (Blocked Agent):**
```
Agent starts tests -> BLOCKED waiting for subprocess -> Cannot answer queries
User: "What's the status?" -> No response (agent is blocked)
Tests complete 10 minutes later -> Agent responds
```

**With Monitor (Responsive Agent):**
```
Agent starts Monitor -> Agent RESPONSIVE -> Can answer queries
User: "What's the status?" -> Agent responds immediately: "4/12 tests passed"
Tests complete -> Agent reports
```

### Monitor Tool Usage

**Basic Pattern:**
```python
Monitor(
    description="Running tests for auth_system",
    command="python run_tests.py",
    timeout_ms=900000,  # 15 minutes
    persistent=True
)

# While Monitor runs:
# - Each line of output is streamed to agent
# - Agent can respond to user queries
# - Agent tracks/parses test results
# - Agent is NOT blocked
```

### What Agent Does While Monitor Runs

**1. Parse Output (Silently)**
- Track test passes/failures from Monitor output
- Maintain internal state of progress
- Do NOT print every line (minimize token usage)

**2. Respond to Queries (Instantly)**
```
User: "What is the test status?"
Agent: "Running. Progress: 5/12 passed. No hangs detected. Est. 3 min remaining."
```

**3. Detect Issues (Proactively)**
- If no output for 30+ seconds: escalate hang
- If test failures detected: report immediately via PushNotification
- If timeout reached: escalate to orchestrator

### Example Timeline (10-minute test suite)

**Old Way (Blocked):**
```
T=0:00   Tests start -> Agent BLOCKED
T=2:15   User: "Status?" -> No response
T=4:30   User: "Still running?" -> No response
T=10:00  Tests finish -> Agent responds
```

**New Way (Monitor - Responsive):**
```
T=0:00   Monitor starts -> Agent RESPONSIVE
T=2:15   User: "Status?"
         Agent: "2/10 tests done. Running test_login_3."
T=4:30   User: "Still running?"
         Agent: "Yes. 5/10 passing. 0 failures so far."
T=10:00  Tests finish -> Final report sent
```

### Implementation Checklist (For Tester)

- [GO] Start Flask server (if needed)
- [GO] Launch test runner in background with safe wrapper
- [GO] Start Monitor to tail log files
- [GO] Initialize tracking state (passed/failed counters)
- [GO] Maintain silent mode (parse output internally, don't spam)
- [GO] Report failures immediately via PushNotification (breaks silence)
- [GO] Respond instantly to user queries
- [GO] Detect hangs (30s+ no output)
- [GO] Send final report when Monitor completes

### Safe Wrapper (For Test Execution)

Use `tests/run_tests_safe.py` instead of directly running tests:

**Why:**
- [OK] Unbuffered output (`-u` flag) -- exceptions written to disk immediately
- [OK] Catches all exceptions before process death
- [OK] Logs to `tests/testResults/run_tests.log` -- visible even if tests crash
- [OK] Never silently dies -- all failures are recorded
- [OK] 30-minute timeout -- prevents infinite hangs

**Usage:**
```python
# Start safe wrapper in background
Bash(
    command="python -u tests/run_tests_safe.py",
    run_in_background=True
)

# Monitor tails test results log
Monitor(
    command="tail -f tests/testResults/run_tests.log",
    timeout_ms=900000,
    persistent=True
)
```

### Silent Mode for Token Efficiency

**SILENT MODE = Minimize token usage, but report failures immediately**

```
WHILE Monitor is running:
  [SILENT] Parse output internally
  [SILENT] Update test_state counters
  [SILENT] Do NOT print every test result
  [IMMEDIATE] When failure detected: PushNotification (breaks silence)
  
WHEN user asks "status?":
  [INSTANT] Read test_state
  [INSTANT] Report current progress (no waiting)
  
WHEN Monitor completes:
  [IMMEDIATE] Send final SendMessage to User
```

---

## Timestamp Logging (All Agents - Team Mode)

Print timestamps to console at 5 key milestones:
1. **[TIMESTAMP] Task received** -- when work arrives
2. **[TIMESTAMP] Work started** -- when you begin actual work
3. **[TIMESTAMP] Work in progress** -- periodic updates during work
4. **[TIMESTAMP] Work completed** -- when done, before sending report
5. **[TIMESTAMP] Report sent** -- after SendMessage delivery

**Format:** `[2026-04-28 14:32:15] Task received - creating tests`

**Why:** Visibility into agent progress, hang detection (30s+ no output = hung), and debugging.

---

## Feature Development Workflow: Roles at a Glance

### Quick Reference

| Agent | Input | Output | Escalation |
|-------|-------|--------|-----------|
| **ProductOwner** | Requirements file or user input | `_output/*/spec.md` | Ambiguous requirements |
| **TestCreator** | Spec file + progress file | `tests/test_*.py` with tests | Unclear test requirements |
| **Developer** | Spec + test file | Modified implementation files | Same test fails 2x |
| **Tester** | Test command from Skill | Test results report | Test hangs (30s+ no output) |
| **Skill (Team Lead)** | User requirements or work request | Routes agents, tracks progress | Starting-message timeout, workflow timeout |

---


## Team Mode vs. Solo Mode

### Team Mode (Working with Skill/Team Lead)
- Receive requests from Skill (team lead)
- Send updates via SendMessage to `@User:`
- Escalate questions to User
- Progress tracked in `_output/*/progress.md`

### Solo Mode (Working directly with user)
- Receive requests from user
- Report to user in text/conversation
- Ask user directly for clarifications
- User manages progress tracking

Most agents work in both modes seamlessly. Just match your communication style to who you're reporting to.

---

## Progress File Updates (MANDATORY - All Agents)

**The progress file is the ONLY source of truth for workflow state.** You MUST update it after completing your work.

### File Location & Format

**Location:** `_output/[feature-name]/progress.md`

**Created by:** ProductOwner at start of workflow

**Updated by:** Each agent as they complete work

### Critical Rule: Update Before Reporting

**BEFORE you send your completion report, you MUST:**
1. Read the current progress file: `_output/[name]/progress.md`
2. Find your section
3. Update the relevant status field
4. Save the file
5. THEN send your completion report via SendMessage

**If you don't update the file, the Skill (team lead) can't route correctly. This breaks the entire workflow.**

### Status Values (Exact Spelling Required)

```
Tests: PENDING | IN_PROGRESS | DONE
Development: PENDING | IN_PROGRESS | DONE
Testing: PENDING | IN_PROGRESS | PASSED | FAILED
```

### What Each Agent Updates

**ProductOwner:** Creates file at start
```
# Feature Progress: {feature_name}
**Status:** In Progress

## Phase 1: Specification
[x] Specification created
Spec file: _output/{feature_name}/spec.md
Completed: [date]

## Phase 2: Test Creation
[ ] Integration tests created
Test file: tests/test_{feature_name}.py
Completed: [pending]

## Phase 3: Development
[ ] Feature implemented
Files modified: [pending]
Completed: [pending]

## Phase 4: Testing
[ ] All tests passing
Completed: [pending]
```

**TestCreator:** After creating tests
```
Tests: DONE
- test_login_with_valid_email
- test_login_fails_with_invalid_password
- test_password_reset_email_sent

Development: PENDING
Testing: PENDING
```

**Developer:** After implementing code
```
Tests: DONE
Development: DONE
- Modified: sage/web/routes.py
- Modified: sage/chat/engine.py

Testing: PENDING
```

**Tester:** After running tests
```
Tests: DONE
Development: DONE
Testing: PASSED
- All 3 tests passed

OR

Testing: FAILED
- test_login_fails_with_invalid_password: Expected 401, got 200
```

### Routing Based on Progress File

Orchestrator reads file to determine next step:

```
IF ProductOwner != DONE -> Route to ProductOwner
ELSE IF Tests != DONE -> Route to TestCreator
ELSE IF Development != DONE -> Route to Developer
ELSE IF Testing = PENDING -> Route to Tester
ELSE IF Testing = FAILED -> Route to Developer (fix)
ELSE IF Testing = PASSED -> Feature complete
```

### Important Rules

- [RULE] Location is always `_output/[name]/progress.md`
- [RULE] Status values must match exactly (case-sensitive)
- [RULE] Update BEFORE sending completion report
- [RULE] List test function names, not test descriptions
- [RULE] List file paths relative to project root
- [RULE] Never modify progress file yourself; agents update their own sections
- [RULE] Orchestrator reads but never modifies

## Progress File Concurrent Write Safety (All Agents)

**Problem:** Multiple agents write to the same progress file. Without coordination, writes can be lost.

**Solution:** Always use read-modify-write pattern with backoff for retries.

### Safe Update Procedure

1. **Read entire file** -> Parse into memory
2. **Locate your section** -> Find the part you own (ProductOwner -> Phase 1, TestCreator -> Test names, etc.)
3. **Merge your changes** -> Update only your section in the in-memory version
4. **Write atomically** -> Single file write operation (not multiple writes)
5. **On conflict (write fails)** -> Retry up to 3 times with 1-second backoff between attempts
6. **On persistent failure** -> Escalate immediately (something is wrong)

### Implementation Example (Python)

```python
import time

def update_progress_file_safely(feature_name, section_name, new_content):
    """Update progress file with atomic write and retry logic."""
    progress_file = f"_output/{feature_name}/progress.md"
    max_retries = 3
    
    for attempt in range(max_retries):
        try:
            # [1] Read entire file
            with open(progress_file, 'r') as f:
                content = f.read()
            
            # [2] Locate your section (simplified example)
            # Real implementation would parse markdown structure
            lines = content.split('\n')
            
            # [3] Merge your changes into memory
            updated_lines = []
            in_your_section = False
            for line in lines:
                if f"## {section_name}" in line:
                    in_your_section = True
                    updated_lines.append(line)
                    updated_lines.append(new_content)
                elif line.startswith("## ") and in_your_section:
                    in_your_section = False
                    updated_lines.append(line)
                elif not in_your_section:
                    updated_lines.append(line)
            
            updated_content = '\n'.join(updated_lines)
            
            # [4] Write atomically
            with open(progress_file, 'w') as f:
                f.write(updated_content)
            
            return True  # Success
        
        except IOError as e:
            if attempt < max_retries - 1:
                time.sleep(1)  # Backoff
                continue
            else:
                # [6] Persistent failure -- escalate
                raise Exception(f"Failed to update progress file after {max_retries} attempts: {e}")
```

### Key Points

- **Atomic write:** One `f.write()` call, not multiple
- **Backoff:** 1-second delay between retries (not immediate retry)
- **Max 3 retries:** If file is still locked after 3 tries, escalate
- **Read first:** Never assume file state since last read
- **Your section only:** Only modify the part you own; preserve all other sections exactly

---

## Before You Start Work (Quick Checklist)

- [ ] Feature name extracted? (snake_case)
- [ ] Task understood?
- [ ] Ready to send "Starting on STORY-N" SendMessage within 60s?
- [ ] Know: 60s starting-message deadline, 8 min work timeout, escalate on real BLOCKERs?

---

## Summary: The Protocol in One Page

```
RECEIVE WORK (SendMessage task)
v
Send "Starting on STORY-N" within 60s (one SendMessage, no retry)
v
BEGIN WORK (silent)
v
On question/ambiguity/hang/unrecoverable infra issue -> follow Outcome 3 (BLOCKED):
  v update_story_status.py STORY-N BLOCKED --reason "..."
  v send completion message with BLOCKER payload
v
COMPLETE WORK
v
update_story_status.py STORY-N <next-status>
v
Send ONE completion message (SendMessage) with the outcome payload
v
Accept shutdown_request -> approve=true -> DONE
```

This handbook is referenced by all agents. See your agent-specific file for detailed instructions on your particular role.
