# Project Architecture

## Overview

This is a Kotlin-based web application that provides AI-powered conversational services with support for multiple LLM providers, MCP (Model Context Protocol) integration, and RAG (Retrieval-Augmented Generation) capabilities.

## Architecture Pattern

**Layered Architecture** with Dependency Injection

```
┌─────────────────────────────────────┐
│      HTTP/WebSocket API Layer       │
│         (Application.kt)            │
└─────────────────────────────────────┘
                  │
┌─────────────────────────────────────┐
│        Service Layer                │
│  (Claude, Ollama, MCP Services)     │
└─────────────────────────────────────┘
                  │
┌─────────────────────────────────────┐
│    Repository/Database Layer        │
│  (Exposed ORM Tables)               │
└─────────────────────────────────────┘
                  │
┌─────────────────────────────────────┐
│         PostgreSQL Database         │
└─────────────────────────────────────┘
```

## Core Components

### 1. Application Layer (`Application.kt`)
- **HTTP Server**: Ktor/Netty-based REST API
- **WebSocket Server**: Real-time chat communication
- **Routing**: 10 REST endpoints + 1 WebSocket endpoint
- **Port**: 8080

### 2. Service Layer (`src/main/kotlin/ru/aolenev/services/`)

#### AI/LLM Services
- **ClaudeService**: Claude AI integration (primary LLM)
  - Single prompts
  - Structured responses
  - Conversational chat with auto-summarization
  - Tool-augmented chat
  - RAG-enhanced responses
  - GitHub PR review automation

- **YandexGptService**: Yandex GPT integration
- **OpenAIService**: OpenRouter/OpenAI integration
- **OllamaRagService**: Ollama for embeddings and RAG
  - Text chunking (word-based or custom separators)
  - Embedding generation
  - Vector similarity search
  - File parsing (TXT, JSON, PDF, MD)

#### MCP Services (Model Context Protocol)
- **CommonMcpService**: Base MCP client functionality
  - Session initialization
  - Tool listing
  - Tool execution

- **GitHubMcpService**: GitHub API via MCP
  - Authenticated GitHub operations
  - PR management

- **TurboMcpServer**: Custom Turbo API integration
- **DatabaseMcpServer**: Database operations as MCP tools
- **ShellMcpServer**: Shell command execution as MCP tool

#### Other Services
- **TurboApiService**: Direct Turbo API client
- **CronJobService**: Scheduled task execution

### 3. Repository Layer (`src/main/kotlin/ru/aolenev/repo/`)

Uses **Exposed ORM** for database access:

- **ChatTable**: Stores chat sessions
  - Chat ID, AI role, creation timestamp

- **MessageTable**: Stores chat messages
  - Message content, type (USER/ASSISTANT/SUMMARY), chat reference

- **FuelingStatTable**: Stores fueling statistics
  - Custom domain data

- **RagEmbeddingsTable**: Stores vector embeddings for RAG
  - Text chunks, embeddings (pgvector), similarity search

### 4. Model Layer (`src/main/kotlin/ru/aolenev/model/`)

Data classes for:
- **Claude.kt**: Claude API request/response models
- **MCP.kt**: MCP protocol models
- **Turbo.kt**: Turbo API models

### 5. Dependency Injection (`DIManager.kt`)

**Kodein DI** container with two modules:

- **baseModule**: Infrastructure
  - ObjectMapper (Jackson)
  - HttpClient (OkHttp)
  - DataSource (HikariCP)
  - Database (Exposed)
  - Flyway migrations
  - Config (Typesafe Config)

- **serviceModule**: Business services
  - All service layer components

## Key Features

### 1. Multi-LLM Support
- Claude AI (primary)
- Yandex GPT
- OpenRouter/OpenAI
- Pluggable provider architecture

### 2. Conversational AI
- Stateful chat sessions with history
- Automatic summarization (after 8 messages)
- In-memory caching (Caffeine, 15-min TTL)
- Persistent storage in PostgreSQL

### 3. RAG (Retrieval-Augmented Generation)
- Document processing and chunking
- Embedding generation via Ollama
- Vector similarity search (PostgreSQL pgvector)
- Context enrichment for prompts
- Configurable similarity thresholds

### 4. MCP Integration
- Client mode: Connect to external MCP servers
- Server mode: Expose custom tools
- Tool categories:
  - GitHub operations
  - Database queries
  - Shell commands
  - Custom business logic (Turbo API)

### 5. WebSocket Support
- Real-time bidirectional communication
- Automatic ping/pong keepalive
- Session management
- Connection timeout handling

### 6. GitHub PR Review
- Automated pull request analysis
- Tool-augmented review process
- RAG-enhanced code review
- Comprehensive feedback generation

## Data Flow Examples

### Simple Prompt Flow
```
User → POST /prompt → ClaudeService → Claude API → Response
```

### RAG-Enhanced Query Flow
```
User → POST /help
  ↓
  1. OllamaRagService: Generate embeddings for query
  ↓
  2. RagEmbeddingsTable: Find similar chunks (pgvector)
  ↓
  3. ClaudeService: Enrich prompt with context
  ↓
  4. Claude API: Generate answer with context
  ↓
  Response
```

### Tool-Augmented Conversation Flow
```
User → POST /tooled-conversation
  ↓
  1. ClaudeService: Send prompt with available tools
  ↓
  2. Claude API: Request tool use
  ↓
  3. MCP Service: Execute tool (GitHub, DB, Shell)
  ↓
  4. ClaudeService: Send tool results back to Claude
  ↓
  5. Claude API: Generate final response with tool data
  ↓
  Response
```

### GitHub PR Review Flow
```
User → POST /make-review
  ↓
  1. GitHubMcpService: Initialize MCP session
  ↓
  2. Fetch available GitHub tools
  ↓
  3. OllamaRagService: Enrich prompt with RAG context
  ↓
  4. ClaudeService: Send to Claude with GitHub tools
  ↓
  5. Loop: Claude calls tools → Get PR data → Analyze
  ↓
  6. Claude generates comprehensive review
  ↓
  Response
```

## Technology Stack

### Backend Framework
- **Ktor**: Web framework (REST + WebSocket)
- **Netty**: HTTP server engine
- **Kotlin**: Primary language

### Database
- **PostgreSQL**: Primary database
- **pgvector**: Vector similarity search extension
- **Exposed**: ORM framework
- **HikariCP**: Connection pooling
- **Flyway**: Database migrations

### HTTP & Serialization
- **OkHttp**: HTTP client
- **Jackson**: JSON serialization

### Caching
- **Caffeine**: In-memory cache for chat sessions

### Dependency Injection
- **Kodein DI**: Dependency injection framework

### External APIs
- **Claude API**: Anthropic's Claude AI
- **Yandex GPT**: Yandex AI services
- **OpenRouter**: LLM routing service
- **Ollama**: Local embedding generation
- **GitHub API**: Via MCP integration

## Configuration

Configuration via `application.conf` (Typesafe Config):
- Database connection settings
- MCP server URLs
- API endpoints

Environment variables:
- `ANTHROPIC_API_KEY`: Claude API access
- `GITHUB_PAT`: GitHub API access token
- `YANDEX_API_KEY`: Yandex GPT access (if configured)
- Database credentials

## Database Schema

### Core Tables
1. **chats**: Chat sessions
   - id (PK)
   - ai_role
   - created_at

2. **messages**: Chat messages
   - id (PK)
   - chat_id (FK)
   - content
   - message_type
   - created_at

3. **rag_embeddings**: Vector embeddings
   - id (PK)
   - chunk (text content)
   - embedding (vector)
   - created_at

4. **fueling_stats**: Domain-specific data
   - id (PK)
   - Various fuel-related fields

## API Endpoints

### REST API (10 endpoints)
1. `POST /prompt` - Single prompt to multiple LLMs
2. `POST /structured-prompt` - Structured response
3. `POST /conversation` - Chat with auto-summary
4. `POST /finite-conversation` - Tool-terminated chat
5. `POST /tooled-conversation` - Chat with MCP tools + RAG
6. `POST /mcp/tools` - Initialize MCP session
7. `POST /mcp-server/tools` - MCP server operations
8. `POST /rag/process` - Process embeddings
9. `POST /help` - RAG-enhanced Q&A
10. `POST /make-review` - Automated PR review

### WebSocket API (1 endpoint)
- `WS /wss/{chatId}` - Real-time chat

## Scalability Considerations

### Strengths
- Stateless API design (except WebSocket)
- Connection pooling (HikariCP)
- In-memory caching (Caffeine)
- Async/coroutine support (Kotlin)

### Limitations
- Single instance design (no distributed cache)
- In-memory WebSocket session storage
- No rate limiting
- No authentication/authorization

## Security

### Current Implementation
- Environment-based secrets
- HTTPS for external API calls
- SQL injection protection (Exposed ORM)

### Missing
- API authentication
- Rate limiting
- Request validation
- CORS configuration
- Input sanitization

## Future Enhancements

Potential improvements:
1. Add authentication/authorization
2. Implement rate limiting
3. Add request/response validation
4. Support distributed deployment
5. Add observability (metrics, tracing)
6. Implement caching strategies
7. Add API versioning
8. Support streaming responses
9. Add background job processing
10. Implement circuit breakers for external APIs