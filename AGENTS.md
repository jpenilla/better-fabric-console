## Fast Start

- See `README.md` for background info.
- To test compilation, run: `./gradlew compileJava`.
- To run checks, use: `./gradlew check`.
- Verify your work at the end even if not explicitly requested.

## Endermux

- Endermux modules live in the `endermux/` git submodule and are wired into this repo via `includeBuild("./endermux")` in `settings.gradle.kts`.
- For any work in Endermux code/docs, read and follow `endermux/AGENTS.md` first.
- When validating Endermux changes from the repo root, either `cd endermux` and run its tasks directly, or use composite-prefixed tasks like `./gradlew :endermux:endermux-common:compileJava :endermux:endermux-server:compileJava :endermux:endermux-client:compileJava`.

## Documentation

- Keep `AGENTS.md` in sync with repository structure and build setup.
- If behavior and docs diverge, fix both in the same change.
