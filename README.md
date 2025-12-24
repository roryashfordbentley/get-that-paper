# Get That Paper (GetThatPaper)

Paper plugin for Minecraft 1.21.1 that tracks per-player block breaks into SQLite and exposes a REST API for leaderboards and web integrations.

## Features

- Records every successful block break with player UUID/name and block material.
- Persists to SQLite (bundled) with simple schema and async writes.
- REST API (CORS-open) for overall top blocks and per-player top blocks.
- Configurable host/port/context path and database filename.

## Requirements

- Java 21+
- Maven 3.9+
- Paper server 1.21.1 (API version 1.21.1)

## Build

```sh
mvn package
```

The shaded plugin jar will be at `target/getthatpaper-1.0.0.jar`.

## Install

1. Copy the jar to your Paper server `plugins/` folder.
2. Start the server once to generate `plugins/GetThatPaper/config.yml`.
3. Adjust config (host/port/context/db filename) if needed.
4. Restart the server.

## Configuration (plugins/GetThatPaper/config.yml)

```yaml
api:
  host: "0.0.0.0" # Bind address for the HTTP API
  port: 8765 # Port for the HTTP API
  context: "/api" # Base path for endpoints

database:
  filename: "block-stats.db" # SQLite file under the plugin data folder

limits:
  defaultTop: 10
```

## REST API

Base URL: `http://<host>:<port><context>` (default `http://<host>:8765/api`). Responses are JSON. CORS is enabled for all origins.

- `GET /blocks/top?limit=10`

  - Returns overall top broken blocks. `limit` optional (1-1000, default 10).

- `GET /blocks/player/{playerName}/top?limit=10`
  - Returns top broken blocks for the given player name (case-insensitive). `limit` optional (1-1000, default 10). Responds 404 if player not seen.

Example:

```sh
curl "http://localhost:8765/api/blocks/top?limit=5"
```

```json
{
  "limit": 5,
  "blocks": [
    { "material": "STONE", "count": 1234 },
    { "material": "DIRT", "count": 987 }
  ]
}
```

## Data model (SQLite)

- `player_names(player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL)`
- `block_breaks(player_uuid TEXT NOT NULL, material TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(player_uuid, material))`

## Security notes

- API is unauthenticated and CORS-open by default. Place behind a reverse proxy or add auth if exposing publicly.

## Extending

- Add more listeners (block place, kills, etc.) and mirror patterns in `DataStore` for new stats.
- Add in-game commands or scheduled announcements to surface leaderboard results.

## Troubleshooting

- If the API port is in use, change `api.port` and restart.
- Check `plugins/BlockStatsAPI/block-stats.db` exists; if missing, ensure the plugin can write to the data folder.
- Enable debug logging in server console if startup fails to initialize the database.
