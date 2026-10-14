# sage-config.yaml Schema

Reference for every field in a project's `sage-config.yaml`.

This file is the **team/paths** config. It does NOT contain HOW-to instructions
for any agent -- those live in `<project_root>/.sage/sage-<agent>-config.yaml`.

---

## Minimal Valid Config

```yaml
project:
  name: "MyProject"
  root_dir: "."
  absolute_root_dir: "/abs/path/to/MyProject"

team:
  name: "myproject-feature-team"
  dev_test_team_name: "myproject-dev-test-team"
  agents:
    full:
      - {name: "ProductOwner",  file: "agents/product-owner.md"}
      - {name: "TestCreator",   file: "agents/test-creator.md"}
      - {name: "Developer",     file: "agents/developer.md"}
      - {name: "Tester",        file: "agents/tester.md"}
    dev_test_only:
      - {name: "Developer",     file: "agents/developer.md"}
      - {name: "Tester",        file: "agents/tester.md"}

paths:
  output_dir: "_output"

limits:
  max_cycles: 5
  max_parallel_workers: 4
  global_timeout_seconds: 3600
```

---

## `project` (required)

```yaml
project:
  name: "static-site-generator"                             # str
  root_dir: "."                                             # str (relative)
  absolute_root_dir: "/abs/path/to/static-site-generator"   # str (absolute) -- on Windows: "C:\\path\\to\\static-site-generator"
```

| Field | Required | Purpose |
|---|---|---|
| `name` | yes | Project identifier (used in team names, logs) |
| `root_dir` | yes | Relative path to project root (from where the loader runs) |
| `absolute_root_dir` | yes | Absolute path. Used to find `.sage/`, given to agents that may run in subprocesses |

---

## `team` (required)

```yaml
team:
  name: "static-site-generator-feature-team"
  dev_test_team_name: "static-site-generator-dev-test-team"
  agents:
    full: [...]
    dev_test_only: [...]
```

| Field | Required | Purpose |
|---|---|---|
| `name` | yes | Team name used in `full` mode |
| `dev_test_team_name` | yes | Team name used in `dev-test-only` mode |
| `agents.full` | yes | List of agents spawned in `full` mode |
| `agents.dev_test_only` | yes | List of agents spawned in `dev-test-only` mode |

Each agent entry:

```yaml
- name: "ProductOwner"           # str -- used in Agent tool, must match AGENT_SLUGS in loader
  file: "agents/product-owner.md" # str -- path relative to sage-feature-team root
```

Recognized agent names (mapped to `.sage/` config files by `_tools/load_agents.py`):

| Agent name | Config file slug |
|---|---|
| `ProductOwner` | `sage-product-owner-config.yaml` |
| `TestCreator`  | `sage-test-creator-config.yaml`  |
| `Developer`    | `sage-developer-config.yaml`     |
| `Tester`       | `sage-tester-config.yaml`        |
| `EpicVerifier` | `sage-epic-verifier-config.yaml` |

---

## `paths` (required)

```yaml
paths:
  output_dir: "_output"          # specs, progress files, anything Skill writes
  sage_dir: ".sage"              # OPTIONAL -- see "Locating .sage/" below
```

| Field | Required | Default | Purpose |
|---|---|---|---|
| `output_dir` | yes | -- | Where the Skill writes specs and progress files (relative to project root) |
| `sage_dir` | no | `<absolute_root_dir>/.sage` | Where the loader looks for per-agent instruction configs |

### Locating `.sage/`

The loader searches in this order:
1. `paths.sage_dir` if set (relative or absolute path)
2. `<absolute_root_dir>/.sage`
3. `<root_dir>/.sage`
4. `./.sage` (cwd fallback)

Most projects don't need to set `sage_dir`. The default -- a `.sage/` directory
in the project root -- is what `setup_project.py` scaffolds.

---

## `limits` (required)

Cycle and timeout bounds the Skill enforces while routing work.

```yaml
limits:
  max_cycles: 5                   # max Developer<->Tester rounds PER STORY before escalation
  max_parallel_workers: 4         # cap on concurrently-spawned worker agents (parallel scheduler)
  global_timeout_seconds: 3600    # wall-clock kill switch for the whole feature run (1 hour)
  timeout_starting_message: 60    # seconds -- worker must send "Starting on STORY-N" within this; on miss, story BLOCKED with reason=ack_timeout
  timeout_work_hard: 480          # seconds -- hard timeout per agent's work step (8 min); on miss, story BLOCKED with reason=work_timeout
  timeout_deadlock_detection: 600 # seconds -- overall workflow stall detection
  timeout_test_hang: 30           # seconds -- Tester escalates if test log silent this long
```

| Field | Default | Purpose |
|---|---|---|
| `max_cycles` | 5 | Max Developer->Tester iterations **per story** (parallel scheduler tracks each story's counter independently) |
| `max_parallel_workers` | 4 | Maximum number of ephemeral per-story worker agents the parallel scheduler runs at once |
| `global_timeout_seconds` | 3600 | Hard wall-clock cap on a full-mode feature run after Phase 1 (PO) approval. Scheduler escalates remaining stories when hit |
| `timeout_starting_message` | 60 | Single deadline for the worker's "Starting on STORY-N" SendMessage. No graduated 30s/45s nudges -- one check at 60s. On miss: send `shutdown_request`, mark story BLOCKED with `ack_timeout`, continue scheduling |
| `timeout_work_hard` | 480 | Hard ceiling on a single work step. On miss: send `shutdown_request`, mark story BLOCKED with `work_timeout` |
| `timeout_deadlock_detection` | 600 | Suspect a deadlock if nothing has happened |
| `timeout_test_hang` | 30 | Tester treats silent log as a hang |

All timeouts must be positive integers. Only `max_cycles` is strictly enforced
by the loader's `validate_config`; the timeouts are conventions used by the
Skill and Tester.

---

## Variables Available to Agent Files

The loader substitutes these `{VAR}` placeholders into every agent prompt:

| Variable | Source |
|---|---|
| `{AGENT_NAME}` | Agent class name (e.g., `Tester`) |
| `{AGENT_NAME_SLUG}` | Slug used in `.sage/sage-<slug>-config.yaml` (e.g., `tester`) |
| `{PROJECT_NAME}` | `project.name` |
| `{PROJECT_ROOT}` | `project.absolute_root_dir` |
| `{TEAM_NAME}` | `team.name` |
| `{DEV_TEST_TEAM_NAME}` | `team.dev_test_team_name` |
| `{OUTPUT_DIR}` | `paths.output_dir` |
| `{PROJECT_INSTRUCTIONS}` | The bulleted `instructions:` list from the agent's `.sage/sage-<slug>-config.yaml` (or a placeholder if the file is empty/missing) |

The `{PROJECT_INSTRUCTIONS}` hook lives in `agents/_BASE.md`, which is
prepended to every role file. Adding the hook to a new agent is automatic.

---

## Example: static-site-generator's config

See `sage-config.yaml` in this directory and `examples/static-site-generator/.sage/` for a
complete working example.
