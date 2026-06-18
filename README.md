# Better Fabric Console

Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more.

Better Fabric Console is configurable through the `better-fabric-console.conf` file generated in the config folder. Requires [Fabric-API](https://modrinth.com/mod/fabric-api).

## Changes

- Updated dependencies, now supports Fabric 26.1.2.
- Fixed Windows Terminal being incorrectly detected as a DUMB terminal, causing tab completion, syntax highlighting, and command history to not work. The `TerminalModeDetection` check in `ConsoleThread` was removed — JLine now always handles input, with its own internal fallback for truly unsupported terminals.
