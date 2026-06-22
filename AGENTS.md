# Repository Instructions

## Documentation Map

- Start with `docs/README.md` when you need to find or place documentation.
- Use `docs/indexes/architecture-index.md` for architecture and package boundaries.
- Use `docs/indexes/standards-index.md` for engineering standards and policies.
- Use `docs/indexes/domains-index.md` for domain rules and ownership.
- Use `docs/indexes/decisions-index.md` for decision records and rationale.
- Use `docs/indexes/runbooks-index.md` for operational procedures.
- Use `docs/indexes/archive-index.md` for historical context that is not current source of truth.

## Hard Rules

- Do not read, print, or commit secrets.
- Do not open `.env`, `.env.test`, local secret files, or credential files unless the user explicitly asks for that specific file.
- If sensitive configuration is needed, ask for non-sensitive values or work from checked-in defaults.
- Do not edit files or documents outside the requested scope.
- Do not call external issue/status APIs such as `gh issue list` unless issue state lookup is part of the request.
- Do not remove monitoring, Sentry, deployment, or runtime configuration without understanding the operational intent.

## Work Entry Rules

- If documentation and code disagree, verify the executable source first: `build.gradle`, `src/main/resources/application.yml`, tests, workflows, Dockerfile, and migration files.
- Treat request/response fields, status codes, error response shapes, DB schema, and deployment settings as external contracts.
- Confirm impact before changing API contracts, DB schema, or deployment configuration.
- If the expected scope grows, stop and clarify the scope before continuing implementation.

## Collaboration

- When there are multiple options, present the recommended option first and ask only for decisions that need user input.
- For major technical choices, explain the reason, alternatives, and operational impact briefly.
- Do not apply review feedback blindly. Evaluate it against project structure, operational impact, and change cost.
- When the user asks to understand a concept, prefer simple analogies and step-by-step explanation.
