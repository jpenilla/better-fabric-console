## Fast Start

- Protocol work starts in `endermux-protocol.md`.
- Know the module split:
  - `src/`: Better Fabric Console integration/mod entrypoints.
  - `endermux-common/`: protocol types, framing, serializer, constants.
  - `endermux-server/`: server transport/session/handlers.
  - `endermux-client/`: client transport/runtime/parser/completer.
- After protocol-related edits, run: `./gradlew :endermux-common:compileJava :endermux-server:compileJava :endermux-client:compileJava`.
- For tests, run: `./gradlew test`.
- If shared APIs or root module code changed, run: `./gradlew compileJava`.

## Documentation

- Keep docs (endermux-protocol.md, AGENTS.md) in sync with implementation.
- If behavior and docs diverge, fix both in the same change.
- For wire-format or protocol semantic changes, bump protocol version and update `endermux-protocol.md` in the same change.
