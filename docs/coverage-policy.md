# Code Coverage Policy

This document is the authoritative source for what counts as 100% code
coverage in the typecast-sdk monorepo. Every SDK's CI gate enforces it,
and every per-SDK PR must follow it.

> See the rollout design at
> [`superpowers/specs/2026-04-07-100-percent-coverage-design.md`](superpowers/specs/2026-04-07-100-percent-coverage-design.md)
> for the larger context.

## Target

Every SDK must reach **100% line + function + branch coverage** on every
pull request and on every push to `main`. Toolchains that cannot measure
branch coverage (`go test -cover`, Swift `xccov`) fall back to **100%
line + function** coverage; everything else must hit branches too.

The percentage is enforced by each SDK's coverage tool itself (e.g.
`fail_under = 100` in `coverage.py`, `--fail-under-lines 100` in
`cargo-llvm-cov`, jacoco `<rule>` with `COVEREDRATIO = 1.00`). CI just
runs `make coverage` and trusts the tool's exit code.

## Pragma whitelist

A line may be excluded from coverage measurement **only** if it falls into
one of the following categories. Anything outside this list must be tested.

### Allowed categories

1. **Physically unreachable on the test host.** Platform-specific code
   paths whose preprocessor or compile-time guard is false on the
   current OS or CPU. Example: a C `#ifdef _WIN32` block on a Linux
   runner. Both halves are still tested — they just run on different
   CI runners. The C SDK uses a CI matrix over Linux, macOS, and Windows
   for exactly this reason.

2. **Language-enforced unreachable.** Code that the language semantics
   say cannot run, including:
   - Rust `unreachable!()`, `unimplemented!()`
   - Kotlin `error("unreachable")`
   - Java private utility-class constructors that throw `AssertionError`
   - Go `panic("unreachable")` immediately following an exhaustive switch
   - Swift `fatalError("unreachable")`

3. **Type-only or generated code.** Files whose contents are not runtime
   behavior:
   - TypeScript files containing only `type` and `interface` declarations
   - Python `if TYPE_CHECKING:` blocks
   - Generated stubs

4. **Build or configuration artifacts.** `examples/`, `dist/`, `lib/`,
   `vite.config.ts`, generated build outputs. These are excluded at the
   tool-config level (collection scope), not via inline pragmas.

5. **Runtime / language ergonomic limits.** Lines the compiler emits
   that have no observable user-facing behavior:
   - Swift `defer` blocks whose unreachable cleanup the compiler still emits
   - C `assert()` failure branches in release builds

### Forbidden exclusions

Inline pragmas may **never** be used to exclude:

- HTTP / network failure handling
- Timeout paths
- Input validation
- Response parsing errors
- Authentication / authorization failures
- Any branch that depends on user-controlled input
- Any code path you "know is rare" — rarity is not a reason to skip

These categories are exactly where bugs hide. Excluding them defeats the
purpose of the gate.

## Pragma format

Every excluded line carries an inline comment with the **category** and
the **reason**.

### Examples per language

**Python:**

```python
if sys.platform == "win32":  # pragma: no cover  # category=platform reason="Windows-only fallback path"
    return _windows_path_resolver(p)
```

**TypeScript / JavaScript (vitest + v8):**

```typescript
/* c8 ignore next 3  -- category=unreachable reason="exhaustive switch over union type" */
default:
    throw new Error('unreachable');
```

**Rust (cargo-llvm-cov):**

```rust
match value {
    Variant::A => handle_a(),
    Variant::B => handle_b(),
    // category=unreachable reason="enum is non-exhaustive but only A/B exist today"
    _ => unreachable!(),
}
```

**Java / Kotlin (jacoco):**

```java
private Util() {
    // category=unreachable reason="utility class private constructor"
    throw new AssertionError("no instances");
}
```

**Go (gocover-cobertura / go test -cover):** Go's cover tool has no
inline pragma syntax. Excluded code lives in files matched by the
`coverignore` Makefile pattern (e.g. `*_unreachable.go`). Each excluded
file's package doc comment must contain
`// coverage: category=... reason=...`.

**C (gcov / lcov):**

```c
#ifdef _WIN32
    // LCOV_EXCL_START -- category=platform reason="Windows-only fallback"
    return windows_resolve(path);
    // LCOV_EXCL_STOP
#else
    return posix_resolve(path);
#endif
```

**Swift (xccov):** Swift has no inline pragma. Excluded files are listed
in the `Makefile` `coverage` target's filter step, with a doc comment in
the file body explaining the category and reason.

## Per-SDK exclusion budget

Each SDK has a starting budget of **30 excluded lines maximum** counted
across all pragma categories combined. This is a starting threshold and
will be revisited after the first 1–2 SDK PRs land with real data.

Going over the budget is not a unilateral decision: the SDK PR must
either reduce the count by writing more tests, or open a separate policy
PR adjusting the budget with justification.

## PR body declaration

Every per-SDK coverage PR includes a one-line summary of the budget use
in its description, in this format:

```
Excluded: 7 lines (platform=4, unreachable=2, type-only=1)
```

If the line is missing, the PR is not approvable.

## When the gate fails

If a CI run reports < 100% on a branch, the policy is:

1. **Add a test** that exercises the missing line. This is the default
   answer.
2. **Refactor for testability** if the missing line is logically
   unreachable but the structure of the code makes the compiler think
   otherwise. (Example: removing a redundant `default:` after an
   exhaustive switch.)
3. **Add a pragma** only if the line falls into an allowed category and
   the budget has room.

In all three cases, the change is part of the same PR. The CI gate is
not bypassed.
