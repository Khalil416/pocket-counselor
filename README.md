# Pocket Counselor

An AI-powered skill profiling assessment. Users answer open-ended questions, and the system builds a personalized skill profile by analyzing their responses with Google Gemini.

This is not a therapy tool or career advisor -- it's a portfolio project exploring how to wire up AI scoring into a real quiz flow with checkpoints, async processing, and structured results.

## How it works

The quiz has 45 open-ended questions across 3 tiers. As the user answers, each response is sent to Gemini in the background for scoring against 75 micro-skills (things like "active listening", "boundary setting", "cognitive reframing", etc). The user never sees these skill IDs -- they just answer questions and move on.

The backend is intentionally a "dumb shell". It stores scores, adds numbers, and checks thresholds. All the actual skill detection and profile generation happens in the AI prompts.

**Flow:**

1. User starts a session and gets the first question
2. Types an answer (min 10 chars) and submits
3. Backend fires an async scoring call to Gemini -- user immediately gets the next question (no waiting)
4. Every 3 answered questions, the system checks if a checkpoint threshold is met
5. At a checkpoint, user can choose to see results or keep going
6. Results page sends all accumulated scores to Gemini with a different prompt, which generates categories, labels, summaries, strengths, and growth areas

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
- **AI:** Google Gemini API (gemini-2.0-flash)
- **Storage:** In-memory (no database)

## Project structure

```
pocket-counselor/
├── backend/
│   ├── src/main/java/com/pocketcounselor/
│   │   ├── controller/     # REST endpoints, error handling
│   │   ├── service/        # Session logic, AI calls, scoring, prompts
│   │   ├── model/          # Session, Question, Checkpoint, Microskill
│   │   ├── dto/            # Request/response objects
│   │   ├── config/         # CORS, async executor, data loader
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
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey) -- only needed for real Gemini scoring; the app runs without one in mock mode (see Configuration below)

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

To use real Gemini scoring instead of mock data, edit
`backend/src/main/resources/application.properties` and set both:

```properties
ai.mode=real
gemini.api.key=YOUR_KEY_HERE
```

(Get a key from [Google AI Studio](https://aistudio.google.com/app/apikey).)
Setting `gemini.api.key` alone does nothing -- `ai.mode` must also be `real`,
or the app keeps using mock scoring.

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

Each answer goes through a scoring prompt that asks Gemini to:
- Decide if the answer is valid, invalid, or skipped
- Identify which micro-skills are demonstrated
- Assign Fibonacci-scale points (1, 2, 3, 5, 8, or 13) to each detected skill

The backend validates the AI response (checks for valid skill IDs, fibonacci values, correct point totals) and drops anything malformed. If the AI call fails, it retries once, then moves on -- the user is never blocked.

Results are generated by a second prompt that receives the full score map and produces a JSON structure with categories, percentage scores, summaries, strongest areas, and growth areas. The frontend renders this JSON directly.