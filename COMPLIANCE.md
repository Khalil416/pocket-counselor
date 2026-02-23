# SPEC Compliance Map

## SPEC maddesi → kod karşılığı

- Session modeli + memory store: `backend/session.js` (`createSession`, `buildEmptyScores`).
- Min cevap uzunluğu 10: `backend/server.js` (`POST /api/session/:id/answer`).
- Skip max 7 ve AI çağrısız skip: `backend/server.js` (`POST /skip`) + `backend/session.js` (`canSkip`, `skipQuestion`).
- Asenkron scoring (fire-and-forget): `backend/server.js` (`void fireScoring(...)`).
- AI failure retry 1 kez + `ai_failed`: `backend/server.js` (`fireScoring`) + `backend/session.js` (`markAiFailure`).
- Checkpoint sadece answered%3==0: `backend/checkpoint.js` (`checkForCheckpoint`).
- Checkpoint polling endpoint: `GET /api/session/:id/state`.
- 35 soru limiti: `backend/server.js` (`questionsShown >= 35`).
- PROMPT A/B dosyadan okuma: `backend/api.js` (`SCORING_PROMPT`, `RESULTS_PROMPT`).
- Scoring schema validation + fibonacci + allowed skill ids: `backend/session.js` (`validateScoringResponse`).
- Results tek çağrı/cache: `backend/server.js` (`_resultsRequested`, `_resultsCache`).
- AI JSON aynen render (frontend): `frontend/results.js` (`renderResults`).

## “Kod aptal kabuk, AI beyin” garantileri

- Kod kategori hesaplamaz; sadece AI `categories` dizisini render eder.
- Kod skill kalite/etiket üretmez; scoring sonucu doğrudan session’a yansıtılır.
- Kod prompt içerikleri hardcode etmez; yalnızca `prompts/*.txt` okur.
- Kod AI JSON’unu “düzeltmez”; invalid şema durumunda güvenli şekilde yok sayar.
