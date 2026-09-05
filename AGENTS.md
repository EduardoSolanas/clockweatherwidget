# ClockWeatherWidget agent guidance

## Scope and priorities

- This is an Android Kotlin/Jetpack Compose app with minimum SDK 26. Read the build configuration for current compile and target SDK versions.
- Keep product decisions scoped to the request. Do not add feature mandates, platform migrations, or abstractions without evidence.
- Preserve user work in a dirty checkout. Inspect `git status` first, edit only the requested files, and never reset, checkout, clean, or overwrite unrelated changes.
- Prefer simple, idiomatic Kotlin and existing project patterns. Make surgical changes and remove only code made unused by the change.

## Architecture invariants

- The clock is independent of weather/network work. Use the host-driven `TextClock` path for ticking time; weather refreshes must not drive minute updates.
- Room is the persistent source for coherent weather snapshots. Current, hourly, and daily data must be published consistently, using the existing transaction boundary.
- WorkManager schedules approximate background work subject to constraints, Doze, and its minimum interval. Do not promise exact execution times.
- Respect location permissions and distinguish a real fix, a saved location, and a default fallback. Preserve useful saved cache while offline.
- Keep provider, location, timestamp, and cache identity aligned. Do not relabel old weather as a newly detected location after a failed refresh.
- Treat time zones, DST, manual clock changes, and provider observation times explicitly. Avoid unzoned timestamps when an instant or source zone is available.

## Testing discipline

- Follow strict Red → Green → Refactor for behavior changes: write a meaningful failing test, implement the smallest fix, then refactor.
- New tests use real objects and realistic persistence/data flows. Do not add mocks, stubs, or test doubles. Existing legacy tests that use doubles are not precedent for new tests.
- Run `.\gradlew.bat test --no-daemon` for the full Gradle unit test suite before claiming code work is complete, plus relevant lint and integration checks for the change. Report debug/release variant results separately when they differ, including known configuration/test mismatches.
- State device, emulator, provider, network, battery, and deployment limitations honestly. Passing unit tests do not prove launcher, lifecycle, GPS, or live API behavior.
- For doc-only changes, verify prose, referenced paths, links, and the final diff/status; do not invent code tests.

## Delegation and verification

- For bounded implementation, test, lint, git, or verification work, use an available lower-cost agent when the tool is available. Keep architecture decisions, complex debugging, and high-context judgment in the main conversation.
- If delegation is unavailable, proceed directly with the same bounded workflow and state that limitation briefly. Do not create approval loops.
- Independently inspect delegated changes and verification evidence before reporting completion. Rerun checks when changes, failures, or gaps in evidence justify it; avoid repeating unchanged successful checks.

## Workflow

1. Read this file and inspect repository status before acting.
2. State material assumptions in the working update; resolve ambiguity from existing code and tests where possible.
3. Search with `rg`/`rg --files`; inspect focused source, tests, resources, and build configuration.
4. Make the smallest authorized change with `apply_patch`. Do not expose `local.properties`, API keys, signing material, or other secrets in output.
5. Run focused checks, then the full relevant suite. Preserve and report failures that predate or fall outside the change.
6. Review `git diff`, `git status`, and changed paths before handing off.

## Review expectations

- Explain what changed, why, how it was verified, and any material limitation.
- Use absolute clickable file links when reporting local files.
- Keep documentation recommendations evidence-based and label proposed acceptance checks as proposed.
- Do not claim production, device, live-provider, or battery verification without performing it.
