# HTTP API Documentation

This document describes the HTTP API endpoints available in the application.

## Base URL
```
http://localhost:8080
```

## REST API Endpoints

### 1. Single Prompt

Send a single prompt to various GPT services.

**Endpoint:** `POST /prompt`

**Request Body:**
```json
{
  "prompt": "Your question or prompt",
  "systemPrompt": "Optional system prompt",
  "temperature": 0.7,
  "gptService": "claude",
  "model": "optional model name",
  "maxTokens": 1000
}
```

**Parameters:**
- `prompt` (string, required): The user's prompt
- `systemPrompt` (string, optional): System-level instructions for the AI
- `temperature` (number, optional): Controls randomness (0.0 - 1.0)
- `gptService` (string, optional, default: "claude"): Service to use. Options:
  - `"claude"` - Claude AI service
  - `"yandex"` - Yandex GPT service
  - `"openrouter"` - OpenRouter service
- `model` (string, optional): Specific model to use
- `maxTokens` (integer, optional, default: 1000): Maximum tokens in response

**Response (Success - 200 OK):**
```json
{
  "result": "AI response text"
}
```

**Response (Error - 400 Bad Request):**
```json
{
  "error": "Unknown GPT service: xxx. Use 'claude', 'yandex', or 'openrouter'"
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "result": "Cannot send prompt to AI"
}
```

---

### 2. Structured Prompt

Send a prompt and receive a structured response with metadata.

**Endpoint:** `POST /structured-prompt`

**Request Body:**
```json
{
  "prompt": "Your question or prompt",
  "systemPrompt": "Optional system prompt",
  "temperature": 0.7,
  "gptService": "claude",
  "model": "optional model name",
  "maxTokens": 1000
}
```

**Parameters:** Same as Single Prompt endpoint

**Response (Success - 200 OK):**
```json
{
  "result": {
    "currentDateTime": "2024-01-13T10:30:00",
    "response": "AI response text",
    "correlationId": "uuid-correlation-id"
  }
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "result": "Cannot send prompt to AI"
}
```

---

### 3. Conversation

Start or continue a conversation with automatic summarization.

**Endpoint:** `POST /conversation`

**Request Body:**
```json
{
  "chatId": "unique-chat-identifier",
  "prompt": "Your message",
  "systemPrompt": "Optional system instructions"
}
```

**Parameters:**
- `chatId` (string, required): Unique identifier for the conversation
- `prompt` (string, required): User's message in the conversation
- `systemPrompt` (string, optional): System-level instructions for the AI role

**Response (Success - 200 OK):**
```json
{
  "result": "AI response text"
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "result": "Cannot send prompt to AI"
}
```

---

### 4. Finite Conversation

Chat with a finite end determined by a tool.

**Endpoint:** `POST /finite-conversation`

**Request Body:**
```json
{
  "chatId": "unique-chat-identifier",
  "prompt": "Your message",
  "systemPrompt": "Optional system instructions"
}
```

**Parameters:**
- `chatId` (string, required): Unique identifier for the conversation
- `prompt` (string, required): User's message in the conversation
- `systemPrompt` (string, optional): System-level instructions for the AI role

**Response (Success - 200 OK):**
```json
{
  "result": {
    "response": "AI response text",
    "isChatFinished": false
  }
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "result": "Cannot send prompt to AI"
}
```

---

### 5. Tooled Conversation

Chat with tool support and optional RAG (Retrieval-Augmented Generation).

**Endpoint:** `POST /tooled-conversation`

**Request Body:**
```json
{
  "chatId": "unique-chat-identifier",
  "prompt": "Your message",
  "systemPrompt": "Optional system instructions",
  "withRag": true,
  "minSimilarity": 0.7
}
```

**Parameters:**
- `chatId` (string, required): Unique identifier for the conversation
- `prompt` (string, required): User's message in the conversation
- `systemPrompt` (string, optional): System-level instructions for the AI role
- `withRag` (boolean, optional, default: false): Enable RAG for context augmentation
- `minSimilarity` (number, optional, default: 0.7): Minimum similarity threshold for RAG results

**Response (Success - 200 OK):**
```json
{
  "result": "AI response text with tool usage and/or RAG context"
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "result": "Cannot send prompt to AI"
}
```

---

### 6. MCP Tools (Client Mode)

Initialize MCP session and retrieve available tools.

**Endpoint:** `POST /mcp/tools`

**Request Body:** Empty

**Response (Success - 200 OK):**
```json
{
  "tools": [
    {
      "name": "tool_name",
      "description": "Tool description",
      "inputSchema": {},
      "outputSchema": {}
    }
  ]
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "error": "Failed to initialize MCP session"
}
```

---

### 7. MCP Server Tools

Interact with MCP server to list or call tools.

**Endpoint:** `POST /mcp-server/tools`

**Request Body:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

or for calling a tool:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": {
      "param1": "value1"
    }
  }
}
```

**Parameters:**
- `jsonrpc` (string, required): JSON-RPC version (should be "2.0")
- `id` (integer, required): Request identifier
- `method` (string, required): MCP method to execute. Options:
  - `"tools/list"` - List available tools
  - `"tools/call"` - Call a specific tool
- `params` (object, required): Method parameters
  - For `tools/list`: empty object `{}`
  - For `tools/call`:
    - `name` (string): Name of the tool to call
    - `arguments` (object): Tool-specific arguments

**Response (Success - 200 OK) for tools/list:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "tool_name",
        "description": "Tool description",
        "inputSchema": {},
        "outputSchema": {}
      }
    ],
    "isError": false
  }
}
```

**Response (Success - 200 OK) for tools/call:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": "Tool execution result",
    "isError": false
  }
}
```

**Response (Error - 400 Bad Request):**
```json
{
  "error": "Unknown MCP method: xxx"
}
```

---

### 8. RAG Processing

Process and store embeddings for RAG functionality.

**Endpoint:** `POST /rag/process`

**Request Body:**
```json
{
  "fileName": "sample.txt",
  "separator": ["\n\n", "\n"]
}
```

**Parameters:**
- `fileName` (string, optional): Name of the file in resources to process. Supported formats: `.txt`, `.json`, `.pdf`. If not specified, defaults to `"sample.txt"`.
- `separator` (array of strings, optional): List of custom separators to split text into chunks. When provided:
  - Text is split by any of the specified separators instead of word-based chunking
  - Overlap is automatically set to 0
  - Common examples: `["\n\n"]` (double newlines/paragraphs), `["\n"]` (single newlines/lines), `["."]` (sentences), `["\n\n", "\n"]` (paragraphs and lines)
  - If not provided, text is split into chunks of ~300 words with 50-word overlap (default behavior)

**Response (Success - 200 OK):**
```json
{
  "result": "Embeddings processed and stored successfully"
}
```

**Response (Error - 500 Internal Server Error):**
```json
{
  "error": "Error message describing what went wrong"
}
```

**Notes:**
- The file must be located in the application's resources directory
- **Default behavior (no separator)**: Text is split into chunks of ~300 words with 50-word overlap
- **Custom separator behavior**: Text is split by any of the specified separators with no overlap
- When multiple separators are provided, the text is split by whichever separator is encountered
- Embeddings are generated using Ollama
- Embeddings are stored in the database for later RAG queries

**Example with single separator:**
```json
{
  "fileName": "article.txt",
  "separator": ["\n\n"]
}
```
This splits the text by double newlines (paragraphs), treating each paragraph as a separate chunk.

**Example with multiple separators:**
```json
{
  "fileName": "article.txt",
  "separator": ["\n\n", ".", ";"]
}
```
This splits the text by double newlines, periods, or semicolons, treating each resulting segment as a separate chunk.

---

### 9. Help with RAG

Get help by asking questions with automatic context retrieval from the knowledge base.

**Endpoint:** `POST /help`

**Request Body:**
```json
{
  "question": "How do I implement authentication?",
  "minSimilarity": 0.7
}
```

**Parameters:**
- `question` (string, required): The user's question
- `minSimilarity` (number, optional, default: 0.7): Minimum similarity threshold for RAG context retrieval (0.0 - 1.0)

**Response (Success - 200 OK):**
```json
{
  "result": "Based on the knowledge base, here's how to implement authentication: [detailed answer from Claude based on RAG context]"
}
```

**Response (Error - 503 Service Unavailable):**
```json
{
  "result": "Cannot process help request"
}
```

**How it works:**
1. The question is split into chunks and embeddings are generated
2. Similar content is retrieved from the knowledge base using vector similarity search
3. The question is enriched with relevant context from the knowledge base
4. Claude AI generates a comprehensive answer based on both the question and the retrieved context
5. If no relevant context is found, Claude will indicate this in the response

**Notes:**
- Requires embeddings to be pre-processed and stored (use `/rag/process` endpoint first)
- Higher `minSimilarity` values return more precise but potentially fewer results
- Lower `minSimilarity` values return more results but may include less relevant context
- The system automatically filters out blank chunks and deduplicates similar contexts

---

## WebSocket API

### Chat WebSocket

Establish a WebSocket connection for real-time chat communication.

**Endpoint:** `WS /wss/{chatId}`

**Parameters:**
- `chatId` (path parameter, required): Unique identifier for the chat session

**Connection:**
```javascript
const ws = new WebSocket('ws://localhost:8080/wss/my-chat-id');
```

**Features:**
- Automatic ping/pong every 10 seconds to keep connection alive
- Connection timeout after 30 seconds of inactivity
- Support for text and binary frames
- WebSocket compression enabled for frames larger than 4KB

**Incoming Frames:**
- `Frame.Text`: Text messages from clients
- `Frame.Binary`: Binary messages from clients
- `Frame.Ping`: Ping frames from clients (auto-responded with Pong)
- `Frame.Pong`: Pong responses from clients
- `Frame.Close`: Connection close request

**Outgoing Frames:**
- Periodic `Frame.Ping` sent every 10 seconds

**Connection Lifecycle:**
1. Client connects to `/wss/{chatId}`
2. Server registers the session
3. Server starts sending periodic pings
4. Client can send/receive text or binary frames
5. On disconnect or close, session is cleaned up

**Error Handling:**
- Missing `chatId` closes connection with `VIOLATED_POLICY` code
- Connection errors are logged and session is cleaned up
- Failed pings terminate the ping job

---

## Common Response Codes

- `200 OK`: Request successful
- `400 Bad Request`: Invalid request parameters or unknown service/method
- `500 Internal Server Error`: Server-side error during processing
- `503 Service Unavailable`: AI service temporarily unavailable

## Content Type

All REST API endpoints expect and return JSON:
```
Content-Type: application/json
```

## Headers

The API supports the following headers:
- `X-Request-Id`: Optional correlation ID for request tracking
- `X-Forwarded-For`: Client IP address (for logging)

If not provided, correlation IDs are automatically generated.