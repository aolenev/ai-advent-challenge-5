# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Running the Application
```bash
./gradlew run
```

The application starts a Ktor/Netty server on port 8080.

### Database Migrations
```bash
./gradlew flywayMigrate
```

Migration files are located in `src/main/resources/migrations/`.

## High-Level Architecture

### Core Architecture Pattern
This is a **Kotlin/Ktor layered application** with Dependency Injection (Kodein), following a three-tier architecture:
- **API Layer**: HTTP REST + WebSocket endpoints (Application.kt)
- **Service Layer**: AI/LLM services + MCP servers
- **Repository Layer**: Exposed ORM + PostgreSQL

### Key Architectural Concepts

#### 1. MCP (Model Context Protocol) Integration
The project implements both **MCP clients** and **MCP servers**:

- **MCP Clients**: `CommonMcpService` and `GitHubMcpService` act as clients that connect to external MCP servers
- **MCP Servers**: `TurboMcpServer`, `DatabaseMcpServer`, `ShellMcpServer` expose local functionality as MCP tools that can be called by LLMs

**How MCP Works Here**:
1. Tool definitions live in JSON files at `src/main/resources/tool-templates/`
2. Each MCP server loads its tool templates on initialization
3. Services expose `listTools()` to return available tools and `callTool()` to execute them
4. Claude AI can discover and invoke these tools during conversations

#### 2. RAG (Retrieval-Augmented Generation) Architecture
The RAG system (`OllamaRagService`) follows this flow:

1. **Document Processing**: Text is split into chunks (word-based with overlap, or custom separators)
2. **Embedding Generation**: Ollama generates vector embeddings for each chunk
3. **Storage**: Embeddings stored in PostgreSQL with pgvector extension (`RagEmbeddingsTable`)
4. **Retrieval**: Cosine similarity search finds relevant chunks for a query
5. **Augmentation**: Retrieved chunks are injected into Claude prompts as context

#### 3. Multi-LLM Provider Architecture
The application supports multiple LLM providers through a tagged dependency injection pattern:

```kotlin
// In DIManager.kt:
bind<GptService>(tag = "yandex") with singleton { YandexGptService() }
bind<GptService>(tag = "openai") with singleton { OpenAIService() }
bind<ClaudeService>() with singleton { ClaudeService() }
```

Routes dynamically select providers based on request parameters.

#### 4. Chat Session Management with Auto-Summarization
The `ClaudeService` implements conversational memory:

- Messages stored in `ChatTable` and `MessageTable`
- After 8 messages (configurable threshold), conversation auto-summarizes
- Summaries stored as special message type to reduce context length
- Chat cache (Caffeine) maintains in-memory conversation state

#### 5. WebSocket Session Management
`WsSessionsStorage` maintains active WebSocket connections mapped by chatId. The WebSocket route in `WsRoutes.kt`:
- Stores sessions on connection
- Sends periodic ping frames to keep connections alive
- Cleans up sessions on disconnect

### Dependency Injection (Kodein)
All services are registered in `DIManager.kt` using two modules:

- **baseModule**: Infrastructure (HttpClient, ObjectMapper, Database, Config)
- **serviceModule**: Application services (Claude, GPT, MCP servers, etc.)

Access dependencies via: `private val service: ServiceType by context.instance()`

### GitHub MCP Service Tool System
The `GitHubMcpService` provides GitHub operations as MCP tools. When adding new GitHub tools:

1. Create JSON template in `src/main/resources/tool-templates/github_*.json`
2. Define input/output data classes in `model/MCP.kt` with `@JsonProperty` annotations
3. Load tool in `getTools()` method
4. Implement handler in `callTool()` switch statement using GitHub REST API

Current GitHub tools: list PRs, get diff, list issues, create issue, update issue.

### Configuration
Configuration in `src/main/resources/application.conf` uses Typesafe Config format:
- Database connection (PostgreSQL on port 25432)
- MCP server URL
- Turbo API credentials
- Ollama configuration (embeddings model and URL)
- Cron job intervals

Access via: `config.getString("ai-challenge.section.key")`

### Database Schema
Uses Exposed ORM with Flyway migrations:
- **ChatTable**: Chat sessions with AI role
- **MessageTable**: Individual messages with type (USER/ASSISTANT/SUMMARY)
- **FuelingStatTable**: Domain-specific fueling statistics
- **RagEmbeddingsTable**: Text chunks with vector embeddings (requires pgvector extension)

Tables are defined as Kotlin objects extending `Table()` with DSL-based schema definitions.

### Key REST Endpoints

- `POST /prompt`: Single prompt to Claude/Yandex/OpenRouter
- `POST /structured-prompt`: Prompt with structured JSON response
- `POST /conversation`: Chat with auto-summarization
- `POST /tooled-conversation`: Chat with MCP tool support and optional RAG
- `POST /mcp/tools`: Initialize MCP session and list tools
- `POST /mcp-server/tools`: Call local MCP server tools
- `POST /rag/process`: Process documents and generate embeddings
- `POST /help`: RAG-enhanced help system
- `POST /make-review`: Automated GitHub PR review with RAG context

WebSocket endpoint: `WS /wss/{chatId}` for real-time chat streaming.

### Environment Variables
- `GITHUB_PAT`: GitHub Personal Access Token (used by GitHubMcpService)
- Claude API key: Loaded via Anthropic SDK configuration
- Yandex/OpenRouter API keys: Configured in respective service classes
