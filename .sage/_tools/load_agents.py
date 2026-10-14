#!/usr/bin/env python3
"""
Load agents with complete instructions for /sage-feature-team skill.

Reads:
  - sage-config.yaml: minimal team / paths / limits config
  - <project>/.sage/sage-<agent>-config.yaml: per-agent instruction files

Substitutes {PROJECT_INSTRUCTIONS} (and a few path/team variables) into each
agent's generic prompt and returns the result.

Usage:
    python _tools/load_agents.py full
    python _tools/load_agents.py dev-test-only
"""

import json
import re
import sys
from pathlib import Path

import yaml


# ---------------------------------------------------------------------------
# Config discovery
# ---------------------------------------------------------------------------

def load_config(config_path=None):
    """Load sage-config.yaml. Searches CWD, script parent, and walks up."""
    if config_path is not None:
        p = Path(config_path)
        if not p.exists():
            raise FileNotFoundError(f"Config not found: {config_path}")
        return _read_yaml(p)

    candidates = []
    cwd_config = Path("sage-config.yaml")
    if cwd_config.exists():
        candidates.append(cwd_config)

    script_parent = Path(__file__).parent.parent / "sage-config.yaml"
    if script_parent.exists() and script_parent not in candidates:
        candidates.append(script_parent)

    cur = Path.cwd()
    for _ in range(10):
        c = cur / "sage-config.yaml"
        if c.exists() and c not in candidates:
            candidates.append(c)
        if cur.parent == cur:
            break
        cur = cur.parent

    parse_errors = []
    for c in candidates:
        try:
            return _read_yaml(c)
        except Exception as e:
            parse_errors.append(f"{c}: {e}")

    if parse_errors:
        details = "\n  - ".join(parse_errors)
        raise ValueError(f"Found candidate sage-config.yaml file(s) but failed to parse:\n  - {details}")

    locations = "\n  - ".join(str(p) for p in candidates) if candidates else "none"
    raise FileNotFoundError(f"sage-config.yaml not found. Searched:\n  - {locations}")


def _read_yaml(path):
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


# ---------------------------------------------------------------------------
# Per-agent project instructions
# ---------------------------------------------------------------------------

# Map agent class name (used in team config) -> config file slug
AGENT_SLUGS = {
    "ProductOwner": "product-owner",
    "TestCreator": "test-creator",
    "Developer": "developer",
    "Tester": "tester",
    "EpicVerifier": "epic-verifier",
}

# Subdirectory of the resolved sage_dir where load_agents writes the fully
# rendered prompts (one <AgentName>.md per agent + agents.json). The location
# is owned here so it's identical for every caller -- never chosen ad hoc by a
# skill or the orchestrator.
RENDERED_DIRNAME = ".rendered"


def find_sage_dir(config):
    """Locate the project's .sage/ directory.

    Order:
      1. config.paths.sage_dir (explicit)
      2. <project.absolute_root_dir>/.sage
      3. <project.root_dir>/.sage
      4. ./.sage (cwd fallback)
    """
    paths = config.get("paths", {}) or {}
    explicit = paths.get("sage_dir")
    if explicit:
        p = Path(explicit)
        if p.exists():
            return p

    project = config.get("project", {}) or {}
    abs_root = project.get("absolute_root_dir")
    if abs_root:
        p = Path(abs_root) / ".sage"
        if p.exists():
            return p

    root_dir = project.get("root_dir", ".")
    p = Path(root_dir) / ".sage"
    if p.exists():
        return p

    p = Path(".sage")
    if p.exists():
        return p

    return None


def load_agent_instructions(agent_name, sage_dir):
    """Load `instructions: [...]` list from .sage/sage-<agent>-config.yaml.

    Returns the raw list, or [] if the file doesn't exist or has no instructions.
    """
    if sage_dir is None:
        return []

    slug = AGENT_SLUGS.get(agent_name, agent_name.lower())
    config_file = sage_dir / f"sage-{slug}-config.yaml"

    if not config_file.exists():
        return []

    data = _read_yaml(config_file)
    instructions = data.get("instructions", []) or []
    if not isinstance(instructions, list):
        raise ValueError(
            f"{config_file}: 'instructions' must be a list, got {type(instructions).__name__}"
        )
    return [str(i) for i in instructions]


def format_instructions(instructions):
    """Format instruction list as a markdown bullet list for the agent prompt."""
    if not instructions:
        return (
            "_No project-specific instructions configured._\n\n"
            "_Create `.sage/sage-<agent>-config.yaml` with an `instructions:` list "
            "to give this agent project-specific guidance._"
        )
    return "\n".join(f"- {line}" for line in instructions)


# ---------------------------------------------------------------------------
# Agent file loading & variable substitution
# ---------------------------------------------------------------------------

def get_agents_for_mode(config, mode):
    """Return list of {name, file} for the given mode."""
    mode_key = mode.replace("-", "_")
    return (config.get("team", {}).get("agents", {}) or {}).get(mode_key, [])


def substitute(content, variables):
    """Replace {VAR} occurrences with values from `variables`."""
    result = content
    for key, value in variables.items():
        result = result.replace(f"{{{key}}}", str(value))
    return result


def build_agent_prompt(agent_name, agent_file, base_content, role_content, config, sage_dir):
    """Combine base + role files, substitute project instructions and globals."""
    instructions = load_agent_instructions(agent_name, sage_dir)
    project_instructions = format_instructions(instructions)

    project = config.get("project", {}) or {}
    team = config.get("team", {}) or {}
    paths = config.get("paths", {}) or {}

    # SAGE_TOOLS_DIR resolves to the relative path agents should use when
    # invoking helper scripts (update_story_status.py, verify_ac_map.py, etc.)
    # In an installed project the .sage/ directory holds them; when running
    # from the sage-feature-team source repo itself, they live under _tools/.
    sage_tools_dir = ".sage/_tools" if sage_dir is not None else "_tools"

    variables = {
        "AGENT_NAME": agent_name,
        "AGENT_NAME_SLUG": AGENT_SLUGS.get(agent_name, agent_name.lower()),
        "PROJECT_NAME": project.get("name", ""),
        "PROJECT_ROOT": project.get("absolute_root_dir", ""),
        "TEAM_NAME": team.get("name", ""),
        "DEV_TEST_TEAM_NAME": team.get("dev_test_team_name", ""),
        "OUTPUT_DIR": paths.get("output_dir", "_output"),
        "SAGE_TOOLS_DIR": sage_tools_dir,
        "PROJECT_INSTRUCTIONS": project_instructions,
    }

    combined = base_content + "\n\n---\n\n" + role_content
    return substitute(combined, variables)


# ---------------------------------------------------------------------------
# Rendered-prompt cache
# ---------------------------------------------------------------------------

def write_rendered_prompts(sage_dir, result):
    """Persist the rendered prompts to <sage_dir>/.rendered/.

    Writes one <AgentName>.md per agent plus agents.json (the full loader
    result). The path is computed here -- not passed in by the caller -- so
    every skill/run writes to exactly the same place. Sets
    result['rendered_dir'] and returns it, or returns None if sage_dir is
    unknown or the write fails. Never raises: caching must not break a load.
    """
    if sage_dir is None:
        return None
    rendered_dir = Path(sage_dir) / RENDERED_DIRNAME
    try:
        rendered_dir.mkdir(parents=True, exist_ok=True)
        for agent_name, prompt in result["agents"].items():
            (rendered_dir / f"{agent_name}.md").write_text(
                prompt, encoding="utf-8", newline="\n"
            )
        # Set the path before serializing so agents.json records it too.
        result["rendered_dir"] = str(rendered_dir)
        (rendered_dir / "agents.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8", newline="\n"
        )
        return str(rendered_dir)
    except OSError as e:
        print(f"[WARN] could not write rendered prompts to {rendered_dir}: {e}",
              file=sys.stderr)
        return None


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

def validate_mode(mode):
    if mode not in ("full", "dev-test-only"):
        return False, f"Invalid mode '{mode}'. Must be 'full' or 'dev-test-only'."
    return True, None


def validate_config(config):
    for section in ("project", "team"):
        if section not in config:
            raise ValueError(f"Missing required section: {section}")

    project = config.get("project", {})
    if not project.get("name"):
        raise ValueError("project.name is required and cannot be empty")
    # absolute_root_dir is OPTIONAL: find_sage_dir() falls back through
    # paths.sage_dir -> project.root_dir -> ./.sage when it's omitted. Both
    # bundled configs in this repo (repo-root sage-config.yaml and
    # examples/static-site-generator/sage-config.yaml) intentionally omit it
    # so they're portable; setup_project.py writes an absolute path here for
    # real installed projects.

    team = config.get("team", {})
    if not team.get("name"):
        raise ValueError("team.name is required")
    if not team.get("agents"):
        raise ValueError("team.agents is required")


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

def load_agents_for_mode(mode):
    is_valid, error = validate_mode(mode)
    if not is_valid:
        return {"success": False, "error": error}

    try:
        config = load_config()
        validate_config(config)

        agent_specs = get_agents_for_mode(config, mode)
        if not agent_specs:
            return {"success": False, "error": f"No agents configured for mode '{mode}'"}

        script_dir = Path(__file__).parent.parent
        project_root = Path(config.get("project", {}).get("root_dir", "."))
        sage_dir = find_sage_dir(config)

        # Load base template (shared boilerplate)
        base_file = script_dir / "agents" / "_BASE.md"
        if not base_file.exists():
            base_file = project_root / "feature-team" / "agents" / "_BASE.md"
        if not base_file.exists():
            raise FileNotFoundError(f"Base template not found: {base_file}")
        base_content = base_file.read_text(encoding="utf-8")

        agents_dict = {}
        for spec in agent_specs:
            agent_name = spec["name"]
            rel_file = spec["file"]
            agent_file = script_dir / rel_file
            if not agent_file.exists():
                agent_file = project_root / rel_file
            if not agent_file.exists():
                raise FileNotFoundError(f"Agent file not found: {rel_file}")

            role_content = agent_file.read_text(encoding="utf-8")
            agents_dict[agent_name] = build_agent_prompt(
                agent_name, agent_file, base_content, role_content, config, sage_dir
            )

        team = config["team"]
        team_name = team.get("dev_test_team_name") if mode == "dev-test-only" else team.get("name")

        result = {
            "success": True,
            "mode": mode,
            "team_name": team_name,
            "agents": agents_dict,
            "agent_names": [s["name"] for s in agent_specs],
            "sage_dir": str(sage_dir) if sage_dir else None,
            "rendered_dir": None,
            "config_summary": {
                "project_name": config.get("project", {}).get("name"),
                "absolute_root_dir": config.get("project", {}).get("absolute_root_dir"),
            },
        }

        # Persist rendered prompts to a fixed, loader-owned location so every
        # run writes the same place (not left to the calling skill/orchestrator).
        result["rendered_dir"] = write_rendered_prompts(sage_dir, result)
        return result

    except Exception as e:
        return {"success": False, "error": str(e), "error_type": type(e).__name__}


def main():
    if len(sys.argv) < 2:
        print("Usage: load_agents.py <mode>", file=sys.stderr)
        print("  mode: full | dev-test-only", file=sys.stderr)
        sys.exit(1)

    mode = sys.argv[1]
    result = load_agents_for_mode(mode)
    print(json.dumps(result, indent=2))
    sys.exit(0 if result.get("success") else 1)


if __name__ == "__main__":
    main()
