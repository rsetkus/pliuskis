---
name: sage-po
description: Run the ProductOwner agent inline to create a feature spec + per-story YAML files
when_to_use: When you want to create a new feature specification and stories from a feature description, without running the full team workflow
---

# Sage ProductOwner Skill (inline)

This skill runs the ProductOwner role solo: write a feature specification, break it into stories (one YAML per story), iterate on user feedback, and report when approved.

> **Path note:** All `python .sage/_tools/...` commands below assume an installed project (a `.sage/` directory exists at the project root). If you're running this skill from the sage-feature-team source repo itself (no `.sage/` exists), substitute `_tools/...` instead.

---

## Step 1: Parse Input

Usage:
```
/sage-po "Add dark mode"
/sage-po "Add dark mode" --feature add_dark_mode
```

Compute:
- **feature_description** -- everything the user typed (verbatim requirements)
- **feature_name** -- `--feature <name>` if given, else derive from the description: lowercase, replace spaces with underscores (e.g., "Add Dark Mode" -> `add_dark_mode`)

If no feature description was provided, ask the user what feature they want.

---

---

## Step 2: Load Rendered ProductOwner Prompt (for project instructions and role contract)

```bash
python .sage/_tools/load_agents.py full
```

From the JSON, extract `agents.ProductOwner` and `config_summary.absolute_root_dir`. The rendered prompt has two kinds of content -- use them differently:

**Use these sections** (mode-agnostic role contract -- they apply to you):
- `.sage/agents/_BASE.md` § Project-Specific Instructions -- the project's conventions
- `.sage/agents/product-owner.md` § Your Job
- `.sage/agents/product-owner.md` § Spec Format (Markdown) -- exact spec layout
- `.sage/agents/product-owner.md` § Story Format (YAML -- one file per story) -- YAML schema, status legend, optional fields, rules for stories
- `.sage/agents/product-owner.md` § Epics -- when to use multiple epics (default: 1), EPIC YAML schema, rules for epics
- `.sage/agents/product-owner.md` § Critical Rules

**Ignore these sections** (team-mode workflow that does not apply when invoked as a skill):
- `_BASE.md` § STOP / SILENCE RULE / Starting Message / Workflow / Completion Outcomes / Progress File Updates / Key Rules (All Agents)
- `product-owner.md` § ProductOwner Workflow (After Receiving Task) -- this skill defines its own workflow below
- `product-owner.md` § Approval Process (Two Steps) -- this skill handles approval inline (Step 4 below)
- `product-owner.md` § Completion Message Format -- this skill reports to the user as plain text instead

If `success` is false, surface the loader's `error` and stop.

---

## Step 3: Preflight

- Determine `output_dir` (default `_output`); create it if missing
- Compute paths using `feature_name`:
  - `spec_file    = <output_dir>/<feature_name>/spec.md`
  - `epics_dir    = <output_dir>/<feature_name>/epics/`  (always written -- every feature has at least one epic)
  - `stories_dir  = <output_dir>/<feature_name>/stories/`
- If `spec_file` already exists OR `epics_dir` exists and is non-empty OR `stories_dir` exists and is non-empty, ask the user: overwrite, pick a different feature_name, or abort.

---

## Step 4: Do the Work

1. **Read project instructions** from the rendered prompt that are relevant to spec/stories writing.
2. **Create the spec file** at `spec_file` -- follow `.sage/agents/product-owner.md` § Spec Format (Markdown) exactly. Sections: Overview, Requirements, Edge Cases, Technical Notes. **No Acceptance Criteria section** -- AC live inside the story YAMLs. Focus on WHAT, not HOW.
3. **Decide how many epics to create** -- consult `.sage/agents/product-owner.md` § Epics § When to use multiple epics. Default to ONE epic (`EPIC-1`) that wraps every story; split only when warranted. Create `epics_dir` and write the epic YAML files. Every feature has at least one epic.
4. **Create the stories directory** at `stories_dir` and write one YAML file per story (`STORY-1.yaml`, `STORY-2.yaml`, ...). Schema, status legend, optional fields, and rules for stories are in `.sage/agents/product-owner.md` § Story Format (YAML -- one file per story). Key invariants:
   - Every AC for the feature lives in exactly one story (no orphans, no duplicates)
   - AC IDs (`AC1`, `AC2`, ...) unique across the entire feature
   - Story IDs stable (`STORY-1`, ...) -- never renumber after approval
   - Filename matches `id` (`STORY-1.yaml` contains `id: STORY-1`)
   - Story dependencies form a DAG (no cycles) and may only name stories in the same epic
   - All stories start at `status: TODO`
   - Every story file MUST include an `epic: EPIC-N` field; the union of all epics' `story_ids:` must equal the full set of story files
5. **Apply the role's Critical Rules throughout** -- see `.sage/agents/product-owner.md` § Critical Rules (snake_case feature name, AC must be testable, valid YAML, no tests/no code).
6. **Show the user a brief summary** (counts of requirements / epics / stories / total AC) and ask for review. They'll respond with feedback or `APPROVED`.
7. **On feedback:** update the spec/epics/stories, summarize the diff, re-request approval. Loop. **FEEDBACK != APPROVAL** -- only an explicit `APPROVED` finishes the skill.
8. **On `APPROVED`:** report completion to the user as plain text:
   ```
   Spec:        <spec_file>
   Epics dir:   <epics_dir>
   Stories dir: <stories_dir>
   Epics:       EPIC-1.yaml, EPIC-2.yaml, ... (<M> total, all status: TODO)
   Stories:     STORY-1.yaml, STORY-2.yaml, ... (<N> total, all status: TODO)
   AC:          <total_ac> distributed across <N> stories
   ```

---

## What This Skill Does NOT Do

- Does not run TestCreator, Developer, or Tester (use `/sage-test-creator`, `/sage-developer`, `/sage-tester` for those -- or `/sage-feature-team` for the full workflow)
- Does not advance any story past `TODO` -- that's the next agent's job
- Does not create a progress file -- progress files are for the team-orchestrated workflow


---

## Token Tracking (Record)

After reporting to the user, record this skill's estimated token consumption:

```bash
python .sage/_tools/record_worker_usage.py     --feature <feature_name> --role ProductOwner --story - --cycle 1     --inline --output-chars <approximate output chars produced>
```

Inline-mode entries are flagged `estimated: true` in `_output/<feature_name>/tokens.json` because we can't measure exact tokens from inside the main conversation (use `/usage` for the precise session total). Estimate `output-chars` as roughly the size of files you wrote + your final user-facing report. Failure here is non-fatal -- log and continue.
