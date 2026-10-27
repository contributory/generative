---
name: create-skill
description: Create reusable Agent Skills from user requirements. Use when the user asks to create a new skill or define a repeatable AI workflow.
---

# Create an Agent Skill

Turn the user's request into a self-contained, reusable Agent Skill following the Agent Skills specification.

## Workflow

1. Identify the purpose, scope, inputs, desired output, and situations that should activate the skill. Use the user's requirements; ask for missing details only if they are essential.
2. Choose a short, descriptive skill name (1–64 characters): lowercase letters, digits, and single hyphens only; no leading, trailing, or repeated hyphens.
3. Write a concise description (1–1024 characters) saying **what** the skill does and **when** an agent should use it. Include trigger phrases or situations where helpful.
4. Write the skill's complete reusable Markdown instructions. Include a clear workflow, expected output, constraints, validation steps, and examples or edge cases when useful. Make the instructions specific enough for another agent to follow without this conversation.
5. Call `create_skill` with three separate arguments:
   - `name`: the directory-safe skill name.
   - `description`: the skill's discovery description.
   - `content`: **only** the Markdown body of `SKILL.md`, without YAML frontmatter.
6. Confirm the created skill and its name **only after** `create_skill` reports success. If saving fails, report the error rather than claiming the skill was created.

## Requirements

- The saved skill must follow the `SKILL.md` format: YAML frontmatter delimited by `---`, with required `name` and `description` fields, followed by Markdown instructions. The `create_skill` tool creates that structure from the three arguments above.
- Keep the skill focused on one reusable capability, not a one-off answer to the current request.
- Do not embed current conversation details, secrets, credentials, or personal data unless they are essential and explicitly requested.
- Do not assume that external tools, files, packages, or APIs exist. Describe dependencies and preconditions when needed.
- Do not create unrelated skills or overwrite another skill without an explicit user request.

## Example

For a request to make a skill that reviews pull requests:

- `name`: `review-pull-request`
- `description`: `Review code changes for correctness, maintainability, and tests. Use when asked to review a pull request or patch.`
- `content`: Markdown instructions explaining how to inspect the diff, identify risks, check tests, and report findings with file references.
