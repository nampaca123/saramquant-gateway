# CLAUDE.md

- Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.
- All AWS cloud deployments must run through Terraform using GitHub CI/CD from the main branch of GitHub, in order to ensure reproducibility via IaC. All created instance should have a tag `project=saramquant`.
- Local Terraform runs `make check` (validate) only. `plan/apply/destroy/refresh/import/state/force-unlock` and any command touching the shared S3 state are CI-only (`deploy.yml`, OIDC) — never run them locally (a stale local lock freezes all deploys). The `infra/tf` wrapper enforces this outside CI.
- Use Opus subagents for direct code writing; use Sonnet subagents for simple tasks like file deletion or file search to save a token.
- Use "GH_CONFIG_DIR": "C:/Users/a/.config/gh-personal" for this SaramQuant Project.

**Tradeoff:** These guidelines bias toward caution over speed.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.
- `superpowers:brainstorming` before non-trivial code or plan: refine intent via Q&A, get design sign-off.
- When graphify-out/graph.json exists, query the graph with graphify query before reading or searching through files directly.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
  Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must — patch, don't rewrite. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.
  When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.
  The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"
- Drive the build loop with `superpowers:test-driven-development`: failing test first → minimal code to pass → refactor. (The task gate in §9 is this loop's exit condition.)
  For multi-step tasks, state a brief plan:
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
   Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

**Parallelization (on request):** When the user signals they're running multiple concurrent sessions, or when work spans genuinely disjoint files/state and is large enough that wall-clock time matters, use `superpowers:using-git-worktrees` so each session or agent gets an isolated working tree; route shared files (lockfiles, routing, migrations) to a single one. For ordinary single-session tasks, skip this.

## 5. Testing & Definition of Done

**Tests cover predictable, common failure cases. "Done" is verified in two stages, not repeatedly.**

- **Test coverage:** A test file's purpose is thorough verification. Anticipate likely failure points, write tests that cover them, and run the full suite with nothing skipped — not just the happy path — surfacing only failures, not passing output.
- **Two-stage Definition of Done** (gates live in §9, run each stage once):
    1. **Task stage** — (a) test scripts run and pass locally, (b) local server runs and the feature is tested against it.
    2. **PR stage** — the full feature is tested against the **deployed environment** via real `curl` requests. Deployed-env testing runs once here, not per task.
- Skipping a stage's checks means that stage is unfinished.
  **Lightweight data access:** For simple DB or bucket queries for testing, confirm the relevant keys are present in `.env` first, then run them through DuckDB.
  **Log Management:** Store execution metadata (run identity, timing, status, input/output counts, failure cause) to CloudWatch structured logs via try/finally, so that every run — failures included — is recorded as one record per run.

## 6. Structure & Naming

**Organize for navigability. Name for intent. Keep files small.**

- **File size:** Keep each file under ~300 lines. No single file (a "god" service, etc.) should dominate the codebase. Split by responsibility when it grows.
- **Naming:** Files and functions must be unambiguous and reflect their role. Prefer a `verb-object` form (e.g. `parse-config.py`, `validateInput`) so the role is obvious at a glance.

## 7. Comments & Language

- **Comments:** Minimal. Max 2 lines per file, 1 preferred. Write comments in Korean.
- **Direct Communication**: Communicate user with Korean.
- **System text:** All logs, error messages, and system-facing strings in English.

## 9. Gates, Commit & PR

**Two gates only: a light per-task gate and a single PR gate. Work on a branch. PR in Korean.**

- **Branch:** Do major work on a separate branch.
- **Task gate (per task, lightweight):** Commit once the task's own tests pass (the §4 TDD loop) and the §5 task-stage DoD (test scripts + local server) holds. No separate per-task code review — review happens once at the PR gate.
    - **Commit message:** `YYMMDD_TaskNameCamelCase_kyoungin` — today's date, the task in concise CamelCase English, fixed `_kyoungin` suffix. (e.g. `260618_AddCsvParser_kyoungin`)
- **PR gate (once, when the work of major task is whole):** Run in order:
    1. `superpowers:requesting-code-review` on the **whole feature diff**; resolve findings.
    2. §5 PR-stage DoD — deployed-env `curl` tests.
    3. `superpowers:verification-before-completion` — run the verify commands, capture output as proof.
    4. Integrate via `superpowers:finishing-a-development-branch`: full test suite, resolve merge/semantic conflicts, then push and open the PR; remove worktrees.
- **Push & PR:** Keep the PR description in Korean and concise.