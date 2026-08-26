# AllTheLogs

A Fabric client mod for Minecraft **26.2** that searches, imports, and browses Minecraft chat history. Logs are stored in a portable DuckDB database and queried through a transparent in-game screen.

## Requirements

- Minecraft 26.2
- Java 25+
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [oωo (owo-lib)](https://modrinth.com/mod/owo-lib) 0.13.1+26.2 or newer

## Install

Drop the built jar into the instance `mods` folder together with Fabric API and owo-lib.

## Use

Open the browser with the client command:

```
/allthelogs
```

The screen uses Minecraft’s translucent dark overlay (`Surface.VANILLA_TRANSLUCENT` via owo-ui).

- **Search** at the top filters messages with a literal substring (no regex unless you enable it).
- **Filter** opens extra options: regex, case sensitivity (off by default), date bounds, context lines (0–1000), sort order, and page size (`limit`).
- **History** is a virtualised list. Hits are white with the match in light green (`#C8F5C0`). Context lines are grey, darker the further they are in time from the hit (light grey at 0s, medium grey at 15 minutes and beyond).
- Only `limit` matches are kept in memory. Scrolling loads the next page with `offset` and unloads the opposite end, keeping the same row on screen so the list does not jump.
- The **timeline** on the right spans the first hit through the last. Every hit is a marker; date labels sit along the track. Click or drag to jump.
- **Import** (bottom left) opens a second screen. Browse uses the OS native folder/archive picker. Advanced import options sit under a collapsible. Common launcher directories (vanilla, Prism, Lunar, Feather, LabyMod, Badlion) are templates: picking one fills the path and the advanced options. Add more launchers in `CommonLogLocations`.

## What it does on startup

1. Opens (or creates) the database at `<instance>/.allthelogs/logs.duckdb`.
2. Imports this instance’s `logs` directory, skipping `latest.log` (the current session), files that were already imported, and files that contain an AllTheLogs session marker for a capture session already stored.
3. Starts a capture session, writes `AllTheLogs session <uuid>` to the Minecraft log, and records new chat and game messages on a dedicated worker thread, so the client thread is never blocked on DuckDB.

The store is not thread-safe; every read and write is serialised on that worker.

## Config

UI defaults (context lines, page size, regex, case sensitivity, sort) are stored at:

```
<instance>/.config/allthelogs.json
```

## Build

```bash
./gradlew build
```

The remapped jar is written to `build/libs/`. Unit tests live in `src/test` and run with the same task.

## Project layout

- `src/main/java/me/wolfii/allthelogs/data` — LogStore, import, and query engine.
- `src/main/java/me/wolfii/allthelogs/config` — persisted browser settings.
- `src/main/java/me/wolfii/allthelogs/worker` — background thread that owns the store.
- `src/main/java/me/wolfii/allthelogs/search` — search filters and query building.
- `src/main/java/me/wolfii/allthelogs/view` — list rows, highlighting, timeline, paging.
- `src/main/java/me/wolfii/allthelogs/locations` — launcher directories and startup import.
- `src/main/java/me/wolfii/allthelogs/client` — Fabric client, owo-ui screens, native file picker, live chat capture.
- `src/test/java` — unit tests.

## Adding another launcher

Edit `CommonLogLocations.defaults()` and append a `Location` with:

- an id and display name
- OS path templates using `${HOME}`, `${APPDATA}`, `${LOCALAPPDATA}`, `${XDG_DATA_HOME}`, `${USERPROFILE}`
- a path matcher glob and the recursive / nested-archive flags that should be applied when that location is selected
