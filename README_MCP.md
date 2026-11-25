# RepoLens MCP Server (`repolens-mcp.jar`)

Model Context Protocol (MCP) server for RepoLens, providing AI coding assistants (Cursor, Claude Desktop, Claude Code) with tools for GitHub repository health diagnostics, deterministic grading, quality analysis, and cache management.

Built with Java 17 and the official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp`).

---

## Architecture & Communication

- **Transport:** Standard I/O (`stdio`).
- **Standard Output (`stdout`):** Exclusively reserved for JSON-RPC 2.0 protocol messages.
- **Logging & Diagnostics:** Strictly directed to Standard Error (`stderr`) or log files—never stdout.
- **Isolation:** Operates independently of the RepoLens Spring Boot web server. Uses a dedicated cache directory (`REPOLENS_CACHE_DIR`, defaulting to `~/.repolens/mcp-cache`) so it never conflicts with the web application's H2 database.
- **Security & Privacy:** Strictly read-only. Never prints, logs, or returns `GITHUB_TOKEN`. Detection checks return boolean status only.

---

## Build & Artifact Location

To compile, run test suites, and package the standalone fat JAR:

```bash
mvn clean package -DskipTests=false
```

The resulting executable fat JAR is located at:
```
/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar
```

---

## Client Configuration Snippets

Replace `<PATH_TO_JAR>` with the absolute path to your `repolens-mcp.jar`:
`/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar`

### 1. Cursor

**Config file location:**
- **macOS:** `~/Library/Application Support/Cursor/User/globalStorage/cursor.mcp/config.json` (or via Cursor Settings > Features > MCP > Add New Server)
- **Linux:** `~/.config/Cursor/User/globalStorage/cursor.mcp/config.json`
- **Windows:** `%APPDATA%\Cursor\User\globalStorage\cursor.mcp\config.json`

**Snippet:**
```json
{
  "mcpServers": {
    "repolens": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "YOUR_TOKEN_HERE",
        "REPOLENS_CACHE_DIR": ""
      }
    }
  }
}
```

---

### 2. Claude Desktop

**Config file location:**
- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

**Snippet:**
```json
{
  "mcpServers": {
    "repolens": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "YOUR_TOKEN_HERE"
      }
    }
  }
}
```

---

### 3. Claude Code (CLI)

Add via command line:
```bash
claude mcp add repolens -- java -jar "/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar"
```

Or configure via `~/.claude.json`:
```json
{
  "mcpServers": {
    "repolens": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "YOUR_TOKEN_HERE"
      }
    }
  }
}
```

---

## Available Tools

| Tool | Status | Description |
|---|---|---|
| `get_status` | **Live** | Returns RepoLens MCP status, token detection boolean, rate limits, cache directory, and server version (read-only). |
| `analyze_profile` | *Coming Soon* | Deterministically analyzes an entire GitHub profile across repositories. |
| `score_repo` | *Coming Soon* | Calculates the hygiene and quality score for a GitHub repository. |
| `compare_repos` | *Coming Soon* | Compares multiple repositories across hygiene dimensions. |
| `fix_plan` | *Coming Soon* | Generates an optimal fix plan for quality deficiencies using Greedy Weighted Set Cover. |
| `explain_deduction`| *Coming Soon* | Explains point deductions and suggests remediation steps. |
| `find_duplicates` | *Coming Soon* | Detects duplicate repository assignments and boilerplate clones. |

---

## Testing with the MCP Inspector

The official Model Context Protocol Inspector provides an interactive web UI to test stdio servers, inspect tool definitions, and execute tool calls:

```bash
npx @modelcontextprotocol/inspector java -jar "/Users/rohan/Desktop/Repofy copy/mcp-server/target/repolens-mcp.jar"
```

Once launched:
1. Open the Inspector URL shown in terminal (typically `http://localhost:5173`).
2. Verify the server connects and the `initialize` handshake completes.
3. Switch to the **Tools** tab.
4. Click **List Tools**; verify `get_status` appears with description.
5. Click **Call Tool** for `get_status` and inspect the returned JSON response.
