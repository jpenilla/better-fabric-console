## Fast Start

- See `README.md` for background info.
- Endermux modules live in the `endermux/` git submodule and are wired into this repo via `includeBuild("./endermux")` in `settings.gradle.kts`.
- For any work in Endermux code/docs, read and follow `endermux/AGENTS.md` first.
- To test compilation, run: `./gradlew compileJava`.
- To run checks, use: `./gradlew check`.
- Verify your work at the end even if not explicitly requested.

## Documentation

- Keep `AGENTS.md` in sync with repository structure and build setup.
- If behavior and docs diverge, fix both in the same change.
