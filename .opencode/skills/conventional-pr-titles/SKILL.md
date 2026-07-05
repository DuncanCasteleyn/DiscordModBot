---
name: conventional-pr-titles
description: Use when creating, editing, reviewing, or preparing GitHub pull requests to ensure PR titles are always conventional-commit style.
---

# Conventional PR Titles

Use this skill whenever you create, edit, review, or prepare a GitHub pull request.

## Rule

Pull request titles must use conventional commit style:

```text
<type>[optional scope]: <description>
```

Examples:

```text
fix: configure report channel
fix(moderation): configure report channel
feat(reporting): add weekly activity settings
chore(deps): update gradle wrapper
```

## Allowed Types

Prefer the type that matches the primary user-visible or maintenance impact:

- `fix`: bug fixes and behavior corrections
- `feat`: new user-visible functionality
- `docs`: documentation-only changes
- `test`: test-only changes
- `refactor`: code restructuring without behavior changes
- `perf`: performance improvements
- `build`: build system or dependency source changes
- `ci`: CI workflow changes
- `chore`: maintenance that does not fit the above
- `revert`: reverting a previous change

## Requirements

- Use lowercase type and lowercase scope.
- Keep the description concise and imperative or noun-phrase style.
- Do not end the title with a period.
- If the user explicitly asks for a commit type, use that type for the PR title too.
- If there are multiple commits, title the PR for the overall change, not the latest commit only.

## Workflow

Before running `gh pr create` or `gh pr edit --title`, verify the title matches this pattern:

```regex
^(build|chore|ci|docs|feat|fix|perf|refactor|revert|test)(\([a-z0-9-]+\))?: .+
```

If the current or proposed title does not match, rewrite it before creating or updating the pull request.

When using `gh pr create`, pass an explicit conventional title with `--title` rather than relying on GitHub or `gh`
defaults.
