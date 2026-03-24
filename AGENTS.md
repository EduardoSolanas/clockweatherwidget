# Agent Working Rules

## Development Approach
- Always use TDD by default.
- Write or update a failing test first, then implement the smallest code change to pass.
- Refactor only after tests pass.

## Bug Fixes
- Reproduce each bug with a test before changing production code whenever feasible.
- If reproduction by test is not feasible, document why and add the closest guard test.

## Validation
- Run targeted tests for changed areas before install.
- Run broader regression tests when changes affect shared widget timing/update paths.
