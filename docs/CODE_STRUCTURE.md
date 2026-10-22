# Code structure — Smooth Bridge

Based on **Smooth Classes**' Marj4n conventions, with project-specific feature folders.

Java namespace: `org.marj4n.smooth_bridge`.

## Project layout

- `src/main/java/org/marj4n/smooth_bridge/` — Shared/server Java code
- `manager/src/main/java/org/marj4n/smooth_bridge/` — Standalone Java CLI (Smooth Bridge only)
- `src/main/resources/` — Fabric mod metadata, mixins, assets and data
- `tests/` — Standalone/offline tests

## Existing feature folders

- `src/main/java/org/marj4n/smooth_bridge/`: `fabric/`

## Rules

1. Keep `org.marj4n` lowercase and match Java packages to actual folders.
2. Entrypoints stay wired in `fabric.mod.json`; mixin config paths must continue to resolve.
3. Use `api/`, `compat/`, `config/`, `content/`, `integration/`, `mixin/`, `network/`, `registry/`, `runtime/` only when the mod needs them; no empty folder padding.
4. Keep gameplay behavior, mod IDs, public dependency versions, resource namespace and mixin target names unchanged in a layout-only refactor.
5. Shared/server code belongs under `src/main`; client code goes under `src/client` only if this Gradle project uses split source sets.
6. Run `python scripts/check_structure.py` before committing, then build with the repo's Gradle wrapper when dependencies are available.

**Two Gradle modules:** Keep `manager/` outside the Fabric mod's main source set. Keep runtime `smoothbridge/` instance directory and release JAR names unchanged. Prebuilt JARs under `release/` do **not** reflect the source rename until they are rebuilt.

**Known repository caveat:** The manager is a standalone Java CLI and its runtime smoothbridge/ data directory must not be renamed. Existing prebuilt release JARs require a fresh rebuild after source renaming.
