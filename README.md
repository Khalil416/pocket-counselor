# Pocket Counselor

An AI-powered skill profiling assessment. Users answer open-ended questions, and the system builds a personalized skill profile by analyzing their responses with an LLM. Gemini, OpenAI, and Anthropic are all supported -- switching between them is a configuration change, not a code change.

This is not a therapy tool or career advisor -- it's a portfolio project exploring how to wire up AI scoring into a real quiz flow with checkpoints, async processing, and structured results.

## How it works

The quiz has 45 open-ended questions across 3 tiers. As the user answers, each response is sent to the configured LLM in the background for scoring against 75 micro-skills (things like "active listening", "boundary setting", "cognitive reframing", etc). The user never sees these skill IDs -- they just answer questions and move on.

The backend is intentionally a "dumb shell". It stores scores, adds numbers, and checks thresholds. All the actual skill detection and profile generation happens in the AI prompts.

**Flow:**

1. User starts a session and gets the first question
2. Types an answer (min 10 chars) and submits
3. Backend fires an async scoring call to the LLM -- user immediately gets the next question (no waiting)
4. Every 3 answered questions, the system checks if a checkpoint threshold is met
5. At a checkpoint, user can choose to see results or keep going
6. Results page sends all accumulated scores to the LLM with a different prompt, which generates categories, labels, summaries, strengths, and growth areas

**Checkpoints** (based on total points + answered count):

| Checkpoint | Points needed | Min answered |
|-----------|--------------|-------------|
| CP1       | 280          | 10          |
| CP2       | 420          | 15          |
| CP3       | 560          | 20          |
| CP4       | 700          | 25          |

Users can also skip up to 7 questions. Skips don't call the AI or add points.

## Tech stack

- **Frontend:** HTML, CSS, vanilla JS -- no frameworks
- **Backend:** Java 17, Spring Boot 3.2
- **AI:** Pluggable -- Google Gemini (default, gemini-2.0-flash), OpenAI, or Anthropic
- **Storage:** In-memory (no database)

## Project structure

```
pocket-counselor/
├── backend/
│   ├── src/main/java/com/pocketcounselor/
│   │   ├── controller/     # REST endpoints, error handling
│   │   ├── service/        # Session logic, scoring, prompts, AI orchestration
│   │   ├── llm/            # LlmClient interface + one client per provider
│   │   ├── model/          # Session, Question, Checkpoint, Microskill
│   │   ├── dto/            # Request/response objects
│   │   ├── config/         # CORS, async executor, data loader, LLM wiring
│   │   └── store/          # In-memory session store
│   └── src/main/resources/
│       ├── data/           # questions.json, microskills.json
│       └── prompts/        # scoring.txt, results.txt
├── frontend/
│   ├── index.html          # Welcome + quiz + checkpoint modals
│   ├── quiz.js             # Quiz flow, polling, state management
│   ├── results.js          # Results page rendering
│   └── style.css
└── README.md
```

## Setup

### Prerequisites

- Java 17+
- An API key for one of the supported providers -- only needed for real scoring; the app runs without one in mock mode (see Configuration below)

The repo includes the Maven wrapper (`mvnw` / `mvnw.cmd`), so a separate Maven install isn't required.

### Configuration

**No API key needed to try it out.** By default the app runs in mock mode
(`ai.mode=mock`), which returns simulated scoring with no network calls. You
can clone the repo and have the full quiz flow working in one command --
useful for seeing how it's wired up before you commit to an API key.

Copy the example config and edit it:

```bash
cp backend/src/main/resources/application.properties.example backend/src/main/resources/application.properties
```

`application.properties` is gitignored, so your key never gets committed.

To use real scoring instead of mock data, edit
`backend/src/main/resources/application.properties` and set both the mode and a
key for your chosen provider:

```properties
ai.mode=real
ai.provider=gemini
ai.gemini.api-key=YOUR_KEY_HERE
```

Setting a key alone does nothing -- `ai.mode` must also be `real`, or the app
keeps using mock scoring.

### Switching LLM providers

The engine is provider-agnostic. Everything above the wire format -- prompts,
JSON extraction, schema validation, scoring rules -- is shared, so changing
providers is a config edit and nothing else. Set `ai.provider` and supply the
matching key:

```properties
# Google Gemini (default)
ai.provider=gemini
ai.gemini.api-key=...
ai.gemini.model=gemini-2.0-flash

# OpenAI
ai.provider=openai
ai.openai.api-key=...
ai.openai.model=gpt-4o-mini

# Anthropic
ai.provider=anthropic
ai.anthropic.api-key=...
ai.anthropic.model=claude-opus-5
```

`application.properties.example` documents every key, including base URLs,
timeouts, and the provider-specific options (`ai.openai.json-mode`,
`ai.anthropic.max-tokens`, and the `send-temperature` flags described below).

**A note on temperature.** The engine asks for temperature 0.2 when scoring and
0.7 when generating results, but not every model accepts a temperature at all.
Current Anthropic models (Opus 5, Sonnet 5, Opus 4.7/4.8) and OpenAI's
GPT-5-class reasoning models reject non-default sampling parameters with a 400.
Each client decides whether to forward it: `ai.anthropic.send-temperature`
defaults to **false**, `ai.openai.send-temperature` defaults to **true** (the
default `gpt-4o-mini` accepts it). Flip either when you change models. Gemini
always accepts temperature, so it has no such flag.

Configuration is checked at startup: an unknown `ai.provider`, or a selected
provider with no API key or model, fails the application start with an explicit
message rather than blowing up on the first request. In mock mode nothing is
validated, because no provider is contacted.

### Verification status

Be aware of how much of this is actually proven against live APIs:

| Provider | Status |
|---|---|
| **Gemini** | Wire format carried over verbatim from the code this project shipped with; the only path exercised against a live API. |
| **OpenAI** | Written from the published API reference. Covered by unit tests only -- **never sent to a live endpoint.** |
| **Anthropic** | Written from the published API reference. Covered by unit tests only -- **never sent to a live endpoint.** |

The unit tests in `LlmClientWireFormatTest` assert request shape and response
parsing against a stub HTTP server, so they prove the client sends and reads what
we *believe* each API expects -- they cannot catch a mistaken belief about the
API itself. Treat the first real call against OpenAI or Anthropic as the actual
verification, and expect to adjust model names or parameters if a vendor has
moved on.

**How it fits together.** `LlmClient` is a one-method interface --
`String complete(String prompt, double temperature)`. `GeminiClient`,
`OpenAiClient`, and `AnthropicClient` each own exactly one vendor's URL shape,
auth mechanism, request body, and response envelope, and map that vendor's error
format onto a shared `LlmException`. `LlmClientResolver` picks one by name at
startup and wraps it in `RateLimitedLlmClient`. `AiService` sees only the
interface -- it builds prompts, strips markdown fences, parses JSON, and
validates the schema, and has no idea which vendor answered.

Adding a fourth provider means writing one class and registering it in
`LlmConfig`; nothing above the interface changes.

### Rate limiting

The Gemini free tier needs pacing; paid tiers and the other providers generally
do not. That pacing lives in a decorator around the selected client, driven by
config:

```properties
ai.ratelimit.delay-ms=1500        # pause after every successful call
ai.ratelimit.retry-delay-ms=5000  # pause before retrying a 429 or 5xx
ai.ratelimit.max-retries=2        # extra attempts after the first
```

All three default to `0` in code. The values shipped in
`application.properties.example` are tuned for the Gemini free tier -- set them
to `0` for OpenAI, Anthropic, or a paid Gemini tier.

> **Deliberate behavior change:** rate-limit retries previously never fired. The
> old code inspected the response body for a `429` error envelope, but the
> WebClient call raised on non-2xx status *before* that body was ever read, so
> the 429 branch was unreachable. The decorator now catches the HTTP status
> directly, which means 429 retries actually work for the first time. This was
> fixed on purpose during the provider refactor, not introduced by accident.

### Run

```bash
cd backend
./mvnw spring-boot:run
```

On Windows (cmd/PowerShell), use `mvnw.cmd` instead of `./mvnw`. No separate
Maven install is required -- the wrapper downloads the right version
automatically on first run.

Open `http://localhost:8080` in your browser. The backend serves the frontend as static files.

## API

All endpoints are under `/api/session`.

| Method | Endpoint              | What it does                              |
|--------|----------------------|-------------------------------------------|
| POST   | `/start`             | Creates a new session, returns first question |
| POST   | `/{id}/answer`       | Submits an answer, triggers async AI scoring  |
| POST   | `/{id}/skip`         | Skips current question (max 7)               |
| GET    | `/{id}/state`        | Polls session state (checkpoints, warnings)  |
| POST   | `/{id}/results`      | Generates the full skill profile via AI      |
| GET    | `/{id}/results`      | Returns cached results if already generated  |
| GET    | `/{id}/answers`      | Returns all submitted answers for the session |

There's also a `GET /api/health` endpoint.

## Scoring details

Each answer goes through a scoring prompt that asks the model to:
- Decide if the answer is valid, invalid, or skipped
- Identify which micro-skills are demonstrated
- Assign Fibonacci-scale points (1, 2, 3, 5, 8, or 13) to each detected skill

The backend validates the AI response (checks for valid skill IDs, fibonacci values, correct point totals) and drops anything malformed. If the AI call fails, the answer is recorded as an AI failure and the quiz moves on -- the user is never blocked.

Results are generated by a second prompt that receives the full score map and produces a JSON structure with categories, percentage scores, summaries, strongest areas, and growth areas. The frontend renders this JSON directly.