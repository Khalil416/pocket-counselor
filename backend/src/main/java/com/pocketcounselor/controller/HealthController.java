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
        return Map.of(
                "mode", aiConfig.getMode(),
                "keyLoaded", aiConfig.isApiKeyLoaded(),
                "promptFilesOk", promptService.isLoaded(),
                "model", aiConfig.getModel()
        );
    }
}
