#!/usr/bin/env python3
"""
Verify a story's AC implementation map sidecar (the artifact the Developer
writes to prove every AC is wired into production code).

Sidecar path:  _output/<feature>/stories/STORY-N.implementation.md

What this script checks (Gate B):
    1. Coverage       -- every AC in the story YAML has a "## ACn" section.
    2. No deferral    -- no banned word (TODO, placeholder, "future story", ...).
    3. A path         -- every section lists at least one production file path.
    4. Files exist    -- every cited path resolves to a real file under --repo-root.
    5. Lines in range -- every "path:N" cite has N within that file's length.
    6. Symbols real   -- every named code symbol (a backticked identifier on a
                         cite line, e.g. parse_post) appears in the cited
                         production files. Plain English words and dotted
                         expressions are not treated as symbols.

Checks 4-6 read the cited files. They do NOT prove the code is correct, only
that the claim points at real files, real line ranges, and real symbols.

CLI:
    python _tools/verify_ac_map.py STORY-3 --stories-dir _output/<feature>/stories
    python _tools/verify_ac_map.py STORY-3 --stories-dir _output/<feature>/stories --repo-root .

--repo-root is the directory cited paths are resolved against (default: the
current working directory, which is the project root when the orchestrator
runs the Tester).

Exit codes:
    0  success
    1  verification failed (missing sidecar/AC, banned word, no path,
       missing file, line out of range, symbol not found)
    2  invocation error (bad args, story file missing, YAML parse failure)
"""

import argparse
import json
import re
import sys
from pathlib import Path


BANNED_PATTERNS = [
    r"\bdeferred\b",
    r"\bdefer(?:s|ring)?\b",
    r"\bfuture\s+story\b",
    r"\blater\s+story\b",
    r"\bnext\s+pass\b",
    r"\bnext\s+cycle\b",
    r"\bnext\s+pr\b",
    r"\btodo\b",
    r"\bfixme\b",
    r"\bwill\s+be\s+(?:done|implemented|added|wired)\b",
    r"\bto\s+be\s+implemented\b",
    r"\bpunt(?:ed|ing)?\b",
    r"\bplaceholder\b",
    r"\bpending\b",
    r"\bnot\s+implemented\b",
    r"\bnot\s+yet\b",
    r"\bpostponed\b",
]

AC_HEADING_RE = re.compile(r"^##\s+(AC\d+)\b", re.MULTILINE)

PATH_LINE_RE = re.compile(
    r"(?:[\\/])"
    r"|"
    r"\.[a-zA-Z][a-zA-Z0-9]{0,4}\b"
)

# First token of a citation bullet, e.g. "src/ssg/parser.py:124". Must look
# like a real path (slash or extension) with an optional ":<line>". Stricter
# than PATH_LINE_RE so prose like "datetime.date" is not mistaken for a cite.
PATH_TOKEN_RE = re.compile(
    r"^(?:[\w.\-]+[\\/])*[\w\-]+\.[A-Za-z][A-Za-z0-9]{0,4}(?::\d+)?$"
)


def _load_yaml(path):
    try:
        from ruamel.yaml import YAML
        yaml = YAML(typ="safe")
        with open(path, "r", encoding="utf-8") as f:
            return yaml.load(f)
    except ImportError:
        import yaml as pyyaml
        with open(path, "r", encoding="utf-8") as f:
            return pyyaml.safe_load(f)


def get_ac_ids_from_story(story_file):
    """Return ordered list of AC ids declared in the story YAML."""
    data = _load_yaml(story_file)
    if not isinstance(data, dict):
        raise ValueError(f"{story_file} did not contain a YAML mapping")
    ac_list = data.get("acceptance_criteria") or []
    if not isinstance(ac_list, list):
        raise ValueError(f"{story_file}: acceptance_criteria must be a list, got {type(ac_list).__name__}")
    ids = []
    for item in ac_list:
        if isinstance(item, dict) and "id" in item:
            ids.append(str(item["id"]))
        else:
            raise ValueError(f"{story_file}: each AC entry must be a mapping with an 'id' field; got {item!r}")
    return ids


def split_sections(content):
    """Return list of (ac_id, section_text) pairs."""
    matches = list(AC_HEADING_RE.finditer(content))
    sections = []
    for i, m in enumerate(matches):
        ac_id = m.group(1)
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(content)
        sections.append((ac_id, content[start:end]))
    return sections


def has_implementation_path(section_text):
    """True if any non-empty line in the section looks like a file path."""
    for line in section_text.splitlines():
        line = line.strip().lstrip("-*").strip()
        if not line or line.lower().startswith("implemented in"):
            continue
        if PATH_LINE_RE.search(line):
            return True
    return False


def find_banned_words(section_text):
    """Return list of dicts for any banned-word hits."""
    hits = []
    for pat in BANNED_PATTERNS:
        for m in re.finditer(pat, section_text, re.IGNORECASE):
            hits.append({"pattern": pat, "match": m.group(0)})
    return hits


def _is_symbolish(tok):
    """True if a backticked token looks like a real code symbol rather than an
    English word, an acronym, or a dotted expression. Under-claims on purpose."""
    if not re.fullmatch(r"[A-Za-z_]\w*", tok):
        return False
    if len(tok) < 4:
        return False
    if tok.isupper():
        return False
    return ("_" in tok) or (not tok.islower())


def extract_citations(section_text):
    """From one AC section, return (targets, symbols).

    Only real bullet lines ("- path:line ...") are treated as citations, never
    prose. For symbols, only the FIRST backticked token on a cite line is taken
    (the map convention: that token names what is implemented at that location;
    later backticks in the description may reference stdlib or other symbols the
    cited file does not define).
    """
    targets = []
    symbols = set()
    for raw in section_text.splitlines():
        stripped = raw.strip()
        if not (stripped.startswith("-") or stripped.startswith("*")):
            continue
        s = stripped.lstrip("-*").strip()
        if not s or s.lower().startswith("implemented in"):
            continue
        token = s.split()[0].strip("`").rstrip("(),")
        if not PATH_TOKEN_RE.match(token):
            continue
        m = re.fullmatch(r"(?P<path>.+?):(?P<line>\d+)", token)
        if m:
            targets.append({"path": m.group("path"), "line": int(m.group("line"))})
        else:
            targets.append({"path": token, "line": None})
        backticks = re.findall(r"`([^`]+)`", s)
        if backticks:
            first = backticks[0].strip()
            if _is_symbolish(first):
                symbols.add(first)
    return targets, symbols


def verify(story_id, stories_dir, repo_root="."):
    stories_dir = Path(stories_dir)
    repo_root = Path(repo_root)
    story_file = stories_dir / f"{story_id}.yaml"
    sidecar = stories_dir / f"{story_id}.implementation.md"

    if not story_file.exists():
        return {"success": False, "story": story_id,
                "error": f"story file not found: {story_file}",
                "error_type": "story_missing"}, 2

    try:
        ac_ids = get_ac_ids_from_story(story_file)
    except Exception as e:
        return {"success": False, "story": story_id,
                "error": f"failed to read AC list from {story_file}: {e}",
                "error_type": "yaml_error"}, 2

    if not sidecar.exists():
        return {"success": False, "story": story_id,
                "error": f"AC implementation map sidecar not found at {sidecar}. "
                         f"Developer must write this file mapping every AC to its production code.",
                "error_type": "sidecar_missing",
                "expected_sidecar": str(sidecar),
                "expected_ac_count": len(ac_ids)}, 1

    content = sidecar.read_text(encoding="utf-8")
    sections = split_sections(content)
    sectioned_ids = [ac for ac, _ in sections]
    section_lookup = dict(sections)

    missing_ac = [ac for ac in ac_ids if ac not in section_lookup]
    extra_ac = [ac for ac in sectioned_ids if ac not in ac_ids]

    issues = []
    if missing_ac:
        issues.append(f"sidecar missing sections for AC(s): {', '.join(missing_ac)}")

    banned_hits_per_ac = {}
    no_path_acs = []
    missing_files = []
    out_of_range_lines = []
    symbols_per_ac = {}
    file_text_cache = {}

    def file_text(rel_path):
        if rel_path not in file_text_cache:
            full = repo_root / rel_path
            if full.is_file():
                try:
                    file_text_cache[rel_path] = full.read_text(encoding="utf-8", errors="replace")
                except Exception:
                    file_text_cache[rel_path] = None
            else:
                file_text_cache[rel_path] = None
        return file_text_cache[rel_path]

    for ac in ac_ids:
        if ac not in section_lookup:
            continue
        section = section_lookup[ac]

        bw = find_banned_words(section)
        if bw:
            banned_hits_per_ac[ac] = bw

        if not has_implementation_path(section):
            no_path_acs.append(ac)

        targets, symbols = extract_citations(section)
        symbols_per_ac[ac] = symbols

        for t in targets:
            text = file_text(t["path"])
            if text is None:
                missing_files.append({"ac": ac, "path": t["path"]})
                continue
            if t["line"] is not None:
                nlines = text.count("\n") + 1
                if t["line"] < 1 or t["line"] > nlines:
                    out_of_range_lines.append(
                        {"ac": ac, "cite": f'{t["path"]}:{t["line"]}', "file_lines": nlines})

    corpus = "\n".join(t for t in file_text_cache.values() if t)
    unverified_symbols = []
    for ac, symbols in symbols_per_ac.items():
        for sym in sorted(symbols):
            if not re.search(r"\b" + re.escape(sym) + r"\b", corpus):
                unverified_symbols.append({"ac": ac, "symbol": sym})

    if banned_hits_per_ac:
        details = []
        for ac, hits in banned_hits_per_ac.items():
            words = sorted({h["match"] for h in hits})
            details.append(f"{ac}: {', '.join(words)}")
        issues.append("banned-word hits (Developer is rolling deferral forward, not implementing): "
                      + "; ".join(details))

    if no_path_acs:
        issues.append(f"AC(s) with no implementation path listed: {', '.join(no_path_acs)}")

    if missing_files:
        details = [f'{mf["ac"]}: {mf["path"]}' for mf in missing_files]
        issues.append("cited file(s) not found under repo root: " + "; ".join(details))

    if out_of_range_lines:
        details = [f'{o["ac"]}: {o["cite"]} (file has {o["file_lines"]} lines)' for o in out_of_range_lines]
        issues.append("cited line number(s) out of range: " + "; ".join(details))

    if unverified_symbols:
        details = [f'{u["ac"]}: {u["symbol"]}' for u in unverified_symbols]
        issues.append("named symbol(s) not found in any cited file: " + "; ".join(details))

    if not issues:
        return {"success": True, "story": story_id, "sidecar": str(sidecar),
                "ac_count": len(ac_ids), "covered": ac_ids,
                "extra_sections": extra_ac}, 0

    return {"success": False, "story": story_id, "sidecar": str(sidecar),
            "expected_ac": ac_ids, "missing_ac": missing_ac,
            "no_path_ac": no_path_acs, "banned_word_hits": banned_hits_per_ac,
            "missing_files": missing_files, "out_of_range_lines": out_of_range_lines,
            "unverified_symbols": unverified_symbols,
            "error": "; ".join(issues), "error_type": "incomplete"}, 1


def main():
    parser = argparse.ArgumentParser(description="Verify a story's AC implementation map sidecar.")
    parser.add_argument("story_id", help="Story ID (e.g., STORY-3)")
    parser.add_argument("--stories-dir", required=True, help="Path to <feature>/stories/")
    parser.add_argument("--repo-root", default=".",
                        help="Directory cited paths are resolved against (default: current dir)")
    args = parser.parse_args()

    if not re.match(r"^STORY-\d+$", args.story_id):
        print(json.dumps({"success": False, "error": f"invalid story id '{args.story_id}'"}))
        sys.exit(2)

    try:
        result, exit_code = verify(args.story_id, args.stories_dir, args.repo_root)
    except Exception as e:
        result = {"success": False, "error": str(e), "error_type": type(e).__name__}
        exit_code = 2

    print(json.dumps(result))
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
