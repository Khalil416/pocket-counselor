package com.pocketcounselor.controller;

import com.pocketcounselor.config.AiConfig;
import com.pocketcounselor.service.PromptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final AiConfig aiConfig;
    private final PromptService promptService;

    public HealthController(AiConfig aiConfig, PromptService promptService) {
        this.aiConfig = aiConfig;
        this.promptService = promptService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "ai_mode", aiConfig.getMode()
        );
    }

    @GetMapping("/api/ai/status")
    public Map<String, Object> aiStatus() {
        // "model" and "keyLoaded" describe whichever provider ai.provider selects.
        return Map.of(
                "mode", aiConfig.getMode(),
                "provider", aiConfig.normalizedProvider(),
                "keyLoaded", aiConfig.isActiveKeyLoaded(),
                "promptFilesOk", promptService.isLoaded(),
                "model", aiConfig.getActiveModel()
        );
    }
}
