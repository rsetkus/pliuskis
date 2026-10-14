#!/usr/bin/env python3
"""
Atomically update a single story YAML's `status:` field.

Used by all worker agents (TestCreator, Developer, Tester) and the parallel
scheduler in /sage-feature-team. Cross-platform per-file locking ensures
concurrent workers don't trample each other if two updates land on the same
story (rare but possible -- e.g. a re-cycle).

Story YAML schema (one file per story):
    id: STORY-3
    title: ...
    status: TODO   # the only field this script changes
    dependencies: [...]
    description: ...
    acceptance_criteria: [...]

CLI:
    # Path to the story file directly:
    python _tools/update_story_status.py STORY-3 IN_DEV \
        --story-file _output/FEATURE_STORIES_add_dark_mode/STORY-3.yaml

    # Or pass the stories directory and the story id:
    python _tools/update_story_status.py STORY-3 IN_DEV \
        --stories-dir _output/FEATURE_STORIES_add_dark_mode

    # BLOCKED with a reason note (writes blocked_reason: in the YAML):
    python _tools/update_story_status.py STORY-3 BLOCKED \
        --stories-dir ... --reason "waiting on STORY-1"

Output (JSON, one object on stdout):
    {"success": true,  "story": "STORY-3", "old_status": "TODO",  "new_status": "CREATE_TESTS"}
    {"success": false, "error": "story file not found: ..."}

Exit codes:
    0  success
    1  error (file missing, invalid transition, lock failure, YAML parse error, etc.)

Allowed transitions (linear with BLOCKED escape):
    TODO          -> CREATE_TESTS, BLOCKED
    CREATE_TESTS  -> IN_DEV,       BLOCKED
    IN_DEV        -> TESTING,      BLOCKED
    TESTING       -> DONE, IN_DEV, BLOCKED
    DONE          -> (terminal; reject unless --force)
    BLOCKED       -> any non-DONE status (resume)

Pass --force to bypass transition validation. Agents should not need it;
the scheduler may use it for recovery.

Implementation notes:
- Uses ruamel.yaml if available (preserves comments/order). Falls back to
  PyYAML, which doesn't preserve comments -- agents are instructed to keep
  story YAMLs comment-free, so this is acceptable.
- Per-file lock via msvcrt (Windows) or fcntl (POSIX), with a sentinel-file
  fallback. Lock scope is the single story YAML.
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path


VALID_STATUSES = {"TODO", "CREATE_TESTS", "IN_DEV", "TESTING", "DONE", "BLOCKED"}

ALLOWED_TRANSITIONS = {
    "TODO":         {"CREATE_TESTS", "BLOCKED"},
    "CREATE_TESTS": {"IN_DEV", "BLOCKED"},
    "IN_DEV":       {"TESTING", "BLOCKED"},
    "TESTING":      {"DONE", "IN_DEV", "BLOCKED"},
    "DONE":         set(),
    "BLOCKED":      {"TODO", "CREATE_TESTS", "IN_DEV", "TESTING"},
}


# ---------------------------------------------------------------------------
# YAML backend
# ---------------------------------------------------------------------------

def _load_yaml_backend():
    """Return (load_fn, dump_fn) using ruamel.yaml if available, else PyYAML."""
    try:
        from ruamel.yaml import YAML
        yaml = YAML()
        yaml.preserve_quotes = True
        yaml.indent(mapping=2, sequence=4, offset=2)

        def _load(path):
            with open(path, "r", encoding="utf-8") as f:
                return yaml.load(f)

        def _dump(data, path):
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                yaml.dump(data, f)

        return _load, _dump
    except ImportError:
        import yaml as pyyaml

        def _load(path):
            with open(path, "r", encoding="utf-8") as f:
                return pyyaml.safe_load(f)

        def _dump(data, path):
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                pyyaml.safe_dump(data, f, sort_keys=False, default_flow_style=False)

        return _load, _dump


# ---------------------------------------------------------------------------
# Cross-platform per-file lock
# ---------------------------------------------------------------------------

class FileLock:
    def __init__(self, target_path, timeout_seconds=10):
        self.lock_path = Path(str(target_path) + ".lock")
        self.timeout = timeout_seconds
        self._fh = None
        self._mode = None

    def __enter__(self):
        self.lock_path.parent.mkdir(parents=True, exist_ok=True)

        try:
            import msvcrt  # noqa: F401
            self._mode = "msvcrt"
        except ImportError:
            try:
                import fcntl  # noqa: F401
                self._mode = "fcntl"
            except ImportError:
                self._mode = "sentinel"

        deadline = time.time() + self.timeout
        last_err = None
        while time.time() < deadline:
            try:
                if self._mode == "msvcrt":
                    import msvcrt
                    self._fh = open(self.lock_path, "a+b")
                    msvcrt.locking(self._fh.fileno(), msvcrt.LK_NBLCK, 1)
                    return self
                elif self._mode == "fcntl":
                    import fcntl
                    self._fh = open(self.lock_path, "a+b")
                    fcntl.flock(self._fh.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                    return self
                else:
                    flags = os.O_CREAT | os.O_EXCL | os.O_RDWR
                    fd = os.open(str(self.lock_path), flags)
                    self._fh = os.fdopen(fd, "w+b")
                    return self
            except (OSError, IOError) as e:
                last_err = e
                if self._fh is not None:
                    try:
                        self._fh.close()
                    except Exception:
                        pass
                    self._fh = None
                time.sleep(0.05)

        raise TimeoutError(f"could not acquire lock on {self.lock_path} within {self.timeout}s: {last_err}")

    def __exit__(self, exc_type, exc, tb):
        try:
            if self._fh is not None:
                if self._mode == "msvcrt":
                    import msvcrt
                    try:
                        msvcrt.locking(self._fh.fileno(), msvcrt.LK_UNLCK, 1)
                    except OSError:
                        pass
                elif self._mode == "fcntl":
                    import fcntl
                    try:
                        fcntl.flock(self._fh.fileno(), fcntl.LOCK_UN)
                    except OSError:
                        pass
                self._fh.close()
        finally:
            try:
                self.lock_path.unlink()
            except OSError:
                pass


# ---------------------------------------------------------------------------
# Path resolution
# ---------------------------------------------------------------------------

def resolve_story_file(story_id, story_file=None, stories_dir=None):
    if story_file:
        return Path(story_file)
    if stories_dir:
        return Path(stories_dir) / f"{story_id}.yaml"
    raise ValueError("must pass either --story-file or --stories-dir")


# ---------------------------------------------------------------------------
# Update routine
# ---------------------------------------------------------------------------

def update_story_status(story_file, story_id, new_status, reason=None, force=False):
    if new_status not in VALID_STATUSES:
        return {"success": False, "error": f"invalid status '{new_status}'. Valid: {sorted(VALID_STATUSES)}"}

    path = Path(story_file)
    if not path.exists():
        return {"success": False, "error": f"story file not found: {path}"}

    load_yaml, dump_yaml = _load_yaml_backend()

    with FileLock(path):
        try:
            data = load_yaml(path)
        except Exception as e:
            return {"success": False, "error": f"YAML parse failed for {path}: {e}", "error_type": "yaml_parse"}

        if data is None or not isinstance(data, dict):
            return {"success": False, "error": f"{path} did not contain a YAML mapping at the top level"}

        file_id = data.get("id")
        if file_id != story_id:
            return {
                "success": False,
                "error": f"id mismatch: file contains id={file_id!r}, but you asked to update {story_id!r}",
            }

        old_status = data.get("status")
        if old_status not in VALID_STATUSES:
            return {
                "success": False,
                "error": f"current status {old_status!r} in {path} is not a valid status",
            }

        if not force:
            allowed = ALLOWED_TRANSITIONS.get(old_status, set())
            if new_status != old_status and new_status not in allowed:
                return {
                    "success": False,
                    "error": f"invalid transition {old_status} -> {new_status} for {story_id} "
                             f"(allowed: {sorted(allowed) or 'none -- terminal'})",
                    "old_status": old_status,
                    "new_status": new_status,
                }

        if new_status == old_status and (reason is None or data.get("blocked_reason") == reason):
            return {
                "success": True,
                "story": story_id,
                "old_status": old_status,
                "new_status": new_status,
                "noop": True,
            }

        data["status"] = new_status
        if new_status == "BLOCKED":
            if reason:
                data["blocked_reason"] = reason
        else:
            # leaving BLOCKED -- drop blocked_reason if it exists
            if "blocked_reason" in data:
                try:
                    del data["blocked_reason"]
                except Exception:
                    data.pop("blocked_reason", None)

        try:
            dump_yaml(data, path)
        except Exception as e:
            return {"success": False, "error": f"YAML write failed for {path}: {e}", "error_type": "yaml_write"}

        return {
            "success": True,
            "story": story_id,
            "old_status": old_status,
            "new_status": new_status,
            "story_file": str(path),
        }


def main():
    parser = argparse.ArgumentParser(description="Atomically update a single story YAML's status field.")
    parser.add_argument("story_id", help="Story ID (e.g., STORY-3)")
    parser.add_argument("new_status", help=f"New status. One of: {', '.join(sorted(VALID_STATUSES))}")
    grp = parser.add_mutually_exclusive_group(required=True)
    grp.add_argument("--story-file", help="Path to the story YAML file")
    grp.add_argument("--stories-dir", help="Path to the FEATURE_STORIES_<feature>/ directory")
    parser.add_argument("--reason", default=None, help="Reason note (written as blocked_reason for BLOCKED status)")
    parser.add_argument("--force", action="store_true", help="Bypass transition validation")
    args = parser.parse_args()

    try:
        path = resolve_story_file(args.story_id, args.story_file, args.stories_dir)
        result = update_story_status(
            story_file=path,
            story_id=args.story_id,
            new_status=args.new_status,
            reason=args.reason,
            force=args.force,
        )
    except TimeoutError as e:
        result = {"success": False, "error": str(e), "error_type": "lock_timeout"}
    except Exception as e:
        result = {"success": False, "error": str(e), "error_type": type(e).__name__}

    print(json.dumps(result))
    sys.exit(0 if result.get("success") else 1)


if __name__ == "__main__":
    main()
