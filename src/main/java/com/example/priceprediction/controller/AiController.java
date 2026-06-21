package com.example.priceprediction.controller;

import com.example.priceprediction.common.Result;
import com.example.priceprediction.dto.ChatRequest;
import com.example.priceprediction.service.AgentOrchestrator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AgentOrchestrator agentOrchestrator;

    public AiController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @PostMapping("/chat")
    public Result<String> chatWithAi(@RequestBody ChatRequest request) {
        return agentOrchestrator.chat(request);
    }
}
