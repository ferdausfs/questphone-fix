# Project Instructions — ECC Agent Harness (STANDING RULES)

> **BANGLA NOTE (for the project owner):** Ei project e joto kaj-i hok, shob kaj ECC
> agents ar skills diye verify korte hobe. Niche rule ta detail e lekha ache.
> (This file is the permanent record of that rule — every future session must follow it.)

## ⚡ PRIMARY STANDING RULE — ECC Verification Required

**ALL work done in this project MUST be verified using the ECC agents and skills
installed at `.claude/`.**

No task is "done" until the relevant ECC verification step has run:

| Work type | Must be verified with (ECC agent / skill) | ECC command entry point |
|---|---|---|
| Any code change | `code-reviewer` agent + language reviewer (`kotlin-reviewer` for this repo) | `/code-review` |
| New feature / refactor | `tdd-guide` agent + `tdd-workflow` skill (failing test first) | `/test-coverage` |
| Bug fixing | `build-error-resolver` / `kotlin-build-resolver` + relevant `*-testing` skill | `/build-fix` |
| Security-sensitive code (auth, payments, user input, secrets) | `security-reviewer` agent + `security-review` / `security-scan` skill | `/security-scan` |
| Frontend / UI work (Compose) | `kotlin-reviewer` + `frontend-patterns`, `accessibility` skills | `/react-review` (patterns apply to Compose) |
| Database / migration work | `database-reviewer` agent + `database-migrations` skill | — |
| Architecture decisions | `architect` agent + `architecture-decision-records` skill | — |
| Dead code / cleanup | `refactor-cleaner` agent | `/refactor-clean` |
| Silent failures / swallowed errors | `silent-failure-hunter` agent | — |
| Documentation | `doc-updater` agent + `docs-governance` skill | `/update-docs` |
| Forking / open-source releases | `opensource-forker` + `opensource-packager` agents | — |
| Delivery / completion gate | quality-gate hook + `verification-loop` skill | `/quality-gate` |

**Verification workflow to follow for every task (ECC core loop):**

```
plan -> test -> implement -> review -> verify -> remember -> improve
```

- **plan**: use `planner` agent (or `/plan`) before building anything non-trivial.
- **test**: write failing tests first via `tdd-workflow`.
- **implement**: smallest change to green.
- **review**: fresh-context review via `code-reviewer` (never self-approve).
- **verify**: run the full quality gate (`/quality-gate`, `verification-loop`).
- **remember**: capture lessons via `continuous-learning-v2` skill (instincts).
- **improve**: turn repeated wins into reusable skills (`skill-create`).

## Where ECC lives

| Path | What |
|---|---|
| `.claude/agents/*.md` | All 71 agent definitions |
| `.claude/commands/*.md` | All slash-command entry points |
| `.claude/rules/ecc/*` | Language rule packs |
| `.claude/ecc/install-state.json` | Install manifest (profile `full`, 2026-11-06) |

*(The full 314-skill library is not vendored into this repo — keep the install on
your machine at `~/.claude` or your project root from the ECC backup, or fetch the
ECC source repo: github.com/affaan-m/ECC, commit b38be6a.)*

## How to use ECC in this environment

- **In Claude Code / compatible harnesses:** agents auto-invoke per their
  `description` frontmatter; slash commands (`/plan`, `/code-review`,
  `/security-scan`, `/build-fix`, `/gradle-build`, ...) are available directly;
  hooks in the user-level `settings.json` enforce pre-flight and quality gates.
- **In a sandbox (no Claude Code runtime):** before declaring any task done,
  READ the matching agent definition (`.claude/agents/<name>.md`) and apply its
  checklist to the work, then delegate an independent fresh-context review to a
  subagent instructed to act as that ECC reviewer. Output of verification must be
  stated in the final reply.

## Non-negotiables

1. Never skip ECC review because a change is "small" — small diffs get
   `code-reviewer` + `silent-failure-hunter`, at minimum.
2. Security-related work requires `security-reviewer` + `/security-scan` with no
   exceptions (auth, secrets, payments, PII, network config).
3. Tests must exist and pass before "done" (`tdd-workflow`).
4. Bengali/Bangla content or i18n work must pass `bengali-reviewer` /
   `bengali-nlp` checks (Unicode normalization, conjuncts, Banglish handling).
5. Never commit `local.properties`, `keystore.properties`, or any keystore.
