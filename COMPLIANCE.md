# SPEC Compliance Map (Java backend)

- Session in-memory store: `backend-java/src/main/java/com/pocketcounselor/store/SessionStore.java` (`ConcurrentHashMap`).
- Session shape + counters + 77 skills + `INVALID`: `backend-java/src/main/java/com/pocketcounselor/model/Session.java`, `backend-java/src/main/java/com/pocketcounselor/service/SessionService.java`.
- Prompt files loaded from disk: `DataRepository.readPrompt("scoring.txt"|"results.txt")`.
- Async scoring (next question returned immediately): `SessionService.scoreAsync(...)` (background `CompletableFuture`).
- Skip max=7, no AI scoring for skip: `SessionController.skip`, `SessionService.skip`.
- Min answer length 10 and no advance on short text: `SessionController.answer`.
- AI retry once then `ai_failed`: `SessionService.doScore`.
- Schema validation strict (fibonacci points, allowed skill IDs, totals): `AiValidationService.validScoring`.
- Invalid schema => `schema_invalid` + no points/answered increment: `SessionService.doScore`.
- Invalid response_type handling => `invalidAnswers++`, `questionsAnswered++`, `microSkillScores.INVALID++`: `SessionService.doScore`.
- Checkpoint gating and thresholds (`answered % 3 == 0`): `SessionService.checkCheckpoint`.
- No checkpoint replay (`next = reached + 1`): `SessionService.checkCheckpoint`.
- 35 shown-question limit + low-data warning path: `SessionController.answer/skip`, `SessionService.results`.
- Results endpoint returns AI payload with warning flag, does not fabricate categories/summaries: `SessionController.results`, `AiValidationService.validResults`.
