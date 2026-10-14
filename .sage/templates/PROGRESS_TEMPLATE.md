# Feature Progress Template (Quick Reference)

**For complete details:** See ../HANDBOOK.md section "Progress File Updates"

**Location:** `_output/[feature-name]/progress.md`

---

## Template to Copy

```markdown
# Feature Progress: [Feature Name]

**Created:** [Date]  
**Status:** In Progress

## Workflow Status

### Phase 1: Specification
[x] Specification created
Spec file: `_output/[feature-name]/spec.md`
Completed: [Date]

### Phase 2: Test Creation
[ ] Integration tests created
Test file: `<path/to/test_file>`  <!-- TestCreator fills in actual path per project conventions -->
Completed: [pending]

### Phase 3: Development
[ ] Feature implemented
Files modified: [pending]
Completed: [pending]

### Phase 4: Testing
[ ] All tests passing
Completed: [pending]
```

---

## Status Values

```
Tests: PENDING | IN_PROGRESS | DONE
Development: PENDING | IN_PROGRESS | DONE
Testing: PENDING | IN_PROGRESS | PASSED | FAILED
```

---

## Who Updates What

- **ProductOwner** -- Creates file, marks Phase 1: DONE
- **TestCreator** -- Marks Tests: DONE, lists test function names
- **Developer** -- Marks Development: DONE, lists modified files
- **Tester** -- Marks Testing: PASSED or FAILED
- **Skill (Team Lead)** -- Reads only (for routing decisions)

---

**See:** ../HANDBOOK.md -> "Progress File Updates" section for complete details
