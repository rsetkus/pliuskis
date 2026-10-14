#!/usr/bin/env python3
"""
Atomically update a single epic YAML's `status:` field.

Used by the EpicVerifier worker and by the parallel scheduler in
/sage-feature-team to roll up epic status from constituent stories.
Cross-platform per-file locking ensures concurrent writers don't trample
each other.

Epic YAML schema (one file per epic; epics are optional):
    id: EPIC-1
    title: ...
    status: TODO   # TODO | IN_PROGRESS | DONE | VERIFIED | BLOCKED
    depends_on: [EPIC-0]
    story_ids: [STORY-1, STORY-2]
    description: ...
    acceptance: |
      Optional epic-level acceptance criteria

CLI:
    # Path to the epic file directly:
    python _tools/update_epic_status.py EPIC-1 VERIFIED \
        --epic-file _output/add_dark_mode/epics/EPIC-1.yaml

    # Or pass the epics directory:
    python _tools/update_epic_status.py EPIC-1 VERIFIED \
        --epics-dir _output/add_dark_mode/epics

    # BLOCKED with a reason note (writes blocked_reason: in the YAML):
    python _tools/update_epic_status.py EPIC-1 BLOCKED \
        --epics-dir ... --reason "STORY-3 unrecoverable: <details>"

Output (JSON on stdout):
    {"success": true,  "epic": "EPIC-1", "old_status": "DONE", "new_status": "VERIFIED"}
    {"success": false, "error": "epic file not found: ..."}

Exit codes:
    0  success
    1  error (file missing, invalid transition, lock failure, YAML parse error, etc.)

Allowed transitions (rollup states + VERIFIED gate):
    TODO         -> IN_PROGRESS, BLOCKED
    IN_PROGRESS  -> DONE, BLOCKED, TODO         (rollup may walk back if a story re-opens)
    DONE         -> VERIFIED, IN_PROGRESS, BLOCKED   (verifier may re-open stories)
    VERIFIED     -> (terminal; reject unless --force)
    BLOCKED      -> any non-VERIFIED status (resume)

Pass --force to bypass transition validation. The rollup helper uses it
because it computes the new status from authoritative story state and
shouldn't be constrained by the prior epic status.

Reuses the same FileLock + YAML backend approach as update_story_status.py.
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path


VALID_STATUSES = {"TODO", "IN_PROGRESS", "DONE", "VERIFIED", "BLOCKED"}

ALLOWED_TRANSITIONS = {
    "TODO":        {"IN_PROGRESS", "BLOCKED"},
    "IN_PROGRESS": {"DONE", "BLOCKED", "TODO"},
    "DONE":        {"VERIFIED", "IN_PROGRESS", "BLOCKED"},
    "VERIFIED":    set(),
    "BLOCKED":     {"TODO", "IN_PROGRESS", "DONE"},
}


# ---------------------------------------------------------------------------
# YAML backend (shared shape with update_story_status.py)
# ---------------------------------------------------------------------------

def _load_yaml_backend():
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
# Cross-platform per-file lock (mirrors update_story_status.py)
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


def resolve_epic_file(epic_id, epic_file=None, epics_dir=None):
    if epic_file:
        return Path(epic_file)
    if epics_dir:
        return Path(epics_dir) / f"{epic_id}.yaml"
    raise ValueError("must pass either --epic-file or --epics-dir")


def update_epic_status(epic_file, epic_id, new_status, reason=None, force=False):
    if new_status not in VALID_STATUSES:
        return {"success": False, "error": f"invalid status '{new_status}'. Valid: {sorted(VALID_STATUSES)}"}

    path = Path(epic_file)
    if not path.exists():
        return {"success": False, "error": f"epic file not found: {path}"}

    load_yaml, dump_yaml = _load_yaml_backend()

    with FileLock(path):
        try:
            data = load_yaml(path)
        except Exception as e:
            return {"success": False, "error": f"YAML parse failed for {path}: {e}", "error_type": "yaml_parse"}

        if data is None or not isinstance(data, dict):
            return {"success": False, "error": f"{path} did not contain a YAML mapping at the top level"}

        file_id = data.get("id")
        if file_id != epic_id:
            return {
                "success": False,
                "error": f"id mismatch: file contains id={file_id!r}, but you asked to update {epic_id!r}",
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
                    "error": f"invalid transition {old_status} -> {new_status} for {epic_id} "
                             f"(allowed: {sorted(allowed) or 'none -- terminal'})",
                    "old_status": old_status,
                    "new_status": new_status,
                }

        if new_status == old_status and (reason is None or data.get("blocked_reason") == reason):
            return {
                "success": True,
                "epic": epic_id,
                "old_status": old_status,
                "new_status": new_status,
                "noop": True,
            }

        data["status"] = new_status
        if new_status == "BLOCKED":
            if reason:
                data["blocked_reason"] = reason
        else:
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
            "epic": epic_id,
            "old_status": old_status,
            "new_status": new_status,
            "epic_file": str(path),
        }


def main():
    parser = argparse.ArgumentParser(description="Atomically update a single epic YAML's status field.")
    parser.add_argument("epic_id", help="Epic ID (e.g., EPIC-1)")
    parser.add_argument("new_status", help=f"New status. One of: {', '.join(sorted(VALID_STATUSES))}")
    grp = parser.add_mutually_exclusive_group(required=True)
    grp.add_argument("--epic-file", help="Path to the epic YAML file")
    grp.add_argument("--epics-dir", help="Path to the epics/ directory")
    parser.add_argument("--reason", default=None, help="Reason note (written as blocked_reason for BLOCKED status)")
    parser.add_argument("--force", action="store_true", help="Bypass transition validation")
    args = parser.parse_args()

    try:
        path = resolve_epic_file(args.epic_id, args.epic_file, args.epics_dir)
        result = update_epic_status(
            epic_file=path,
            epic_id=args.epic_id,
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
