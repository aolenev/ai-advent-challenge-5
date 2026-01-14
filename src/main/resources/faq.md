# API Usage FAQ

## General Questions

### What is this API?
This API provides AI-powered code review, question answering, and conversational capabilities using Claude AI with RAG (Retrieval-Augmented Generation) and MCP (Model Context Protocol) tools.

### What authentication is required?
- **ANTHROPIC_API_KEY**: Required environment variable for Claude AI API access
- **GITHUB_PAT**: Required environment variable for GitHub integration (Personal Access Token)
- **Database credentials**: Required for RAG functionality (PostgreSQL with pgvector)
- **Ollama service**: Required for embeddings generation

### What are the available endpoints?
- `POST /help` - RAG-enhanced question answering with GitHub tools
- `POST /make-review` - Automated pull request review
- `POST /tooled-conversation` - Interactive conversation with multiple tools
- `POST /rag/process` - Process and store document embeddings

---

## /help Endpoint

### What does the /help endpoint do?
The `/help` endpoint answers questions using:
1. **RAG context** from your knowledge base (pre-processed documents)
2. **GitHub tools** to fetch live repository data (PRs, issues, diffs)
3. **Claude AI** to synthesize comprehensive answers

### How do I use the /help endpoint?

**Request:**
```bash
curl -X POST http://localhost:8080/help \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the current open bug issues?",
    "minSimilarity": 0.7
  }'
```

**Parameters:**
- `question` (string, required): Your question
- `minSimilarity` (number, optional, default: 0.7): Similarity threshold for RAG retrieval (0.0-1.0)

**Response (200 OK):**
```json
{
  "result": "Based on the repository data, there are currently 3 open bug issues: [detailed answer]"
}
```

**Response (503 Service Unavailable):**
```json
{
  "result": "Cannot process help request"
}
```

### What GitHub tools are available in /help?
- `github_list_pull_requests` - List pull requests by state
- `github_get_diff` - Get diff for a specific PR
- `github_list_bug_issues` - List all open issues with label "bug"

### When does Claude use GitHub tools?
Claude intelligently decides when to use tools based on your question:
- Question about bugs/issues → Uses `github_list_bug_issues`
- Question about PRs → Uses `github_list_pull_requests` or `github_get_diff`
- Question about architecture/code patterns → Uses RAG context only

### What repository does /help use for GitHub tools?
By default: `aolenev/ai-advent-challenge-5`

This is hardcoded but can be changed by modifying the default parameters in `ClaudeService.helpWithRag()`.

### How do I adjust the RAG similarity threshold?
Use the `minSimilarity` parameter:
- **0.0-0.3**: More results, potentially less relevant
- **0.4-0.6**: Balanced approach
- **0.7-0.9**: Precise, fewer but highly relevant results (recommended)
- **0.9-1.0**: Very strict, only near-exact matches

**Example:**
```json
{
  "question": "Explain the service architecture",
  "minSimilarity": 0.5
}
```

### What happens if there's no relevant context in the knowledge base?
Claude will:
1. Attempt to use GitHub tools if applicable
2. Clearly state that no relevant information was found
3. Provide general guidance if possible

### How do I populate the knowledge base for RAG?
Use the `/rag/process` endpoint to process and store documents:

```bash
curl -X POST http://localhost:8080/rag/process \
  -H "Content-Type: application/json" \
  -d '{
    "directory": "/path/to/documentation",
    "overwrite": false
  }'
```

---

## /make-review Endpoint

### What does /make-review do?
Automatically reviews the latest pull request in a repository using:
- GitHub tools to fetch PR details and diffs
- RAG context for architecture patterns and best practices
- Claude AI to provide comprehensive code review feedback

### How do I use /make-review?

**Request:**
```bash
curl -X POST http://localhost:8080/make-review \
  -H "Content-Type: application/json" \
  -d '{
    "owner": "aolenev",
    "repo": "ai-advent-challenge-5",
    "minSimilarity": 0.7
  }'
```

**Parameters:**
- `owner` (string, required): GitHub repository owner
- `repo` (string, required): GitHub repository name
- `minSimilarity` (number, optional, default: 0.7): Similarity threshold for RAG context

**Response:**
Returns detailed code review including:
- Compliance with architecture patterns
- Code quality assessment
- Potential bugs and issues
- Improvement suggestions

### What if there are no open pull requests?
The endpoint will return an appropriate message indicating no PRs were found.

### Can I review a specific PR instead of the latest?
Currently, the endpoint reviews the latest open PR. To review a specific PR, you would need to modify the service logic.

---

## /tooled-conversation Endpoint

### What is /tooled-conversation?
An interactive endpoint that supports multi-turn conversations with Claude AI using various MCP tools including:
- GitHub tools (PRs, issues, diffs)
- Database tools (query fuelings data)
- Shell tools (execute commands)

### How do I use /tooled-conversation?

**Request:**
```bash
curl -X POST http://localhost:8080/tooled-conversation \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What are the latest PRs and how many fuelings were completed yesterday?"
  }'
```

**Response:**
```json
{
  "result": "Based on the data: [comprehensive answer using multiple tools]"
}
```

### What tools are available?
- **GitHub Tools**: List PRs, get diffs, list bug issues
- **Database Tools**: Query fueling statistics
- **Shell Tools**: Execute shell commands (use carefully)

---

## /rag/process Endpoint

### What does /rag/process do?
Processes documentation files to create embeddings stored in the database for RAG functionality.

### How do I use /rag/process?

**Request:**
```bash
curl -X POST http://localhost:8080/rag/process \
  -H "Content-Type: application/json" \
  -d '{
    "directory": "/path/to/docs",
    "overwrite": false
  }'
```

**Parameters:**
- `directory` (string, required): Path to directory containing documentation
- `overwrite` (boolean, optional, default: false): Whether to overwrite existing embeddings

### What file types are supported?
The endpoint processes text-based files. Check the implementation for specific formats (typically .md, .txt, code files).

### How long does processing take?
Processing time depends on:
- Number of files
- File sizes
- Ollama service performance
- Database write speed

---

## Troubleshooting

### I'm getting "503 Service Unavailable" errors
**Possible causes:**
1. **Ollama service is down** - Check if Ollama is running
2. **Database connection failed** - Verify PostgreSQL is running and credentials are correct
3. **Claude API error** - Check ANTHROPIC_API_KEY is valid and you have API quota
4. **GitHub API error** - Verify GITHUB_PAT is valid and has required permissions

**Solution:** Check application logs for detailed error messages.

### GitHub tools are not working
**Check:**
1. GITHUB_PAT environment variable is set
2. Token has required permissions: `repo` scope for private repos, `public_repo` for public
3. Repository owner/name are correct
4. Rate limits haven't been exceeded (5000 requests/hour for authenticated requests)

### RAG returns no relevant context
**Solutions:**
1. Lower the `minSimilarity` threshold (try 0.5 or 0.6)
2. Ensure documents were processed via `/rag/process`
3. Check if your question keywords match the document content
4. Verify Ollama service is generating embeddings correctly

### Claude is not using tools when expected
Claude autonomously decides when to use tools. If tools aren't being used:
1. Make your question more explicit about needing live data
2. Check logs to see if tools are being passed to Claude
3. Verify tools are loaded correctly in the service

### Database errors
**Check:**
1. PostgreSQL is running
2. pgvector extension is installed: `CREATE EXTENSION vector;`
3. Required tables exist (check migration scripts)
4. Connection string is correct

---

## Best Practices

### Optimize /help queries
- **Be specific**: "What are the open bug issues?" vs. "Tell me about issues"
- **Adjust similarity**: Start with 0.7, lower if no results
- **Combine with tools**: Ask questions that benefit from live GitHub data

### Effective PR reviews
- **Populate RAG first**: Process your architecture docs and coding standards
- **Use descriptive PR titles**: Helps Claude understand context
- **Review incrementally**: Review PRs regularly rather than large batches

### RAG knowledge base
- **Keep docs updated**: Regularly reprocess with `overwrite: true`
- **Organize content**: Well-structured docs improve retrieval
- **Include examples**: Code examples enhance RAG responses
- **Document patterns**: Architecture patterns, coding standards, best practices

### Token management
- Lower `maxTokens` for simpler queries to save costs
- Use appropriate similarity thresholds to control context size
- Monitor Claude API usage

---

## API Limits

### Rate Limits
- **GitHub API**: 5000 requests/hour (authenticated)
- **Claude API**: Based on your Anthropic plan
- **Database**: No inherent limit, but consider connection pooling

### Token Limits
- **Claude Sonnet 4.5**: 200K input tokens, 4096 output tokens (configurable)
- **RAG context**: Adjust `minSimilarity` to control retrieved chunk size

### Timeouts
Check application configuration for timeout settings. Long-running operations (like PR reviews) may take 30-60 seconds.

---

## Examples

### Example 1: Question about bugs
```bash
curl -X POST http://localhost:8080/help \
  -H "Content-Type: application/json" \
  -d '{
    "question": "How many open bug issues are there and what are they about?",
    "minSimilarity": 0.7
  }'
```

### Example 2: Architecture question
```bash
curl -X POST http://localhost:8080/help \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the recommended pattern for implementing a new service layer?",
    "minSimilarity": 0.8
  }'
```

### Example 3: PR review
```bash
curl -X POST http://localhost:8080/make-review \
  -H "Content-Type: application/json" \
  -d '{
    "owner": "aolenev",
    "repo": "ai-advent-challenge-5",
    "minSimilarity": 0.7
  }'
```

### Example 4: Process documentation
```bash
curl -X POST http://localhost:8080/rag/process \
  -H "Content-Type: application/json" \
  -d '{
    "directory": "/Users/a.olenev/work/hobbies/ai-challenge-5/docs",
    "overwrite": false
  }'
```

---

## Support

For issues or questions:
1. Check application logs for detailed error messages
2. Verify all environment variables are set correctly
3. Test individual components (Ollama, PostgreSQL, GitHub API) separately
4. Review the codebase documentation

## Version Information
- **Claude Model**: claude-sonnet-4-5-20250929
- **Ollama**: Required for embeddings
- **PostgreSQL**: Required with pgvector extension
