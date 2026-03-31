package com.pocketcounselor.controller;

import com.pocketcounselor.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SessionService.SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSessionNotFound(SessionService.SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "session_not_found",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", ex.getCode(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(ResultsNotReadyException.class)
    public ResponseEntity<Map<String, Object>> handleResultsNotReady(ResultsNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", Map.of("code", "RESULTS_NOT_READY",
                        "message", "Results are not ready yet. Please retry once scoring catches up."),
                "readiness", Map.of(
                        "minimum_answered_required", 15,
                        "answered_count", ex.getAnsweredCount(),
                        "last_scored_answer_index", ex.getLastScoredAnswerIndex()
                )
        ));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupported(UnsupportedOperationException ex) {
        log.error("Unsupported operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "unsupported_operation",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", "An unexpected error occurred."
        ));
    }

    // --- Lightweight exception types co-located here ---

    public static class BadRequestException extends RuntimeException {
        private final String code;

        public BadRequestException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }

    public static class ResultsNotReadyException extends RuntimeException {
        private final int answeredCount;
        private final int lastScoredAnswerIndex;

        public ResultsNotReadyException(int answeredCount, int lastScoredAnswerIndex) {
            super("Results not ready");
            this.answeredCount = answeredCount;
            this.lastScoredAnswerIndex = lastScoredAnswerIndex;
        }

        public int getAnsweredCount() { return answeredCount; }
        public int getLastScoredAnswerIndex() { return lastScoredAnswerIndex; }
    }
}
