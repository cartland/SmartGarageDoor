---
description: Check screenshot reference images for blank renders and other health issues.
allowed-tools: Bash(*), Read, Glob, Grep
user-invocable: true
---

# Check Screenshot Health

Detect broken or blank screenshot reference images. This is a warning tool — it never blocks work. Broken screenshots may be app errors, preview errors, or test infrastructure issues.

## When to Use

- After generating screenshots (`/update-android-screenshots`)
- After changing preview composables or theme colors
- When reviewing a PR that modifies UI components
- Proactively during `/repo-check` to surface issues early

## Steps

### 1. Run the health check script

```bash
./scripts/check-screenshot-health.sh
```

This checks for:
- **Blank/tiny PNGs** (< 1KB) — preview rendered empty, usually because it depends on runtime state unavailable in screenshot tests
- **Total vs healthy count** — how many screenshots are working

### 2. Report findings

If blank screenshots are found:
- List each one with its file path
- Explain the likely cause: the preview depends on `rememberAppComponent()` or ViewModel state
- Note that the fix is creating a stateless preview overload with demo data
- **Do not block any work** — this is a high-priority note, not a gate

### 3. Regenerate collections

After screenshots are updated, regenerate the curated collections:

```bash
./scripts/generate-screenshot-collections.sh
```

Check for any missing images in the collection output.

## Output Format

```
Screenshots:   N total, N healthy, N likely blank
Blank files:   (list each with path)
Collections:   N generated, N missing images
Action:        Create stateless preview overloads for blank screenshots
```

## Tips

- Blank screenshots are usually 69-200 bytes (just PNG header, no content)
- A preview that calls `rememberAppComponent()` will always be blank in tests
- The fix pattern: create a `@Composable fun FooPreview()` that passes demo data directly instead of reading from ViewModels
- Reference: `DoorHistoryContentPreview` works because it uses demo data; `HomeContentPreview` is blank because it reads from AppComponent
