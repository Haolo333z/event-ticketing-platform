package com.haolo.ticketing.ai;

import org.springframework.stereotype.Component;

@Component
public class LocalFallbackAiAdapter implements AiAssistantPort {
    @Override
    public String adapterName() {
        return "LOCAL_FALLBACK";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String enhanceResponse(String systemPrompt, String structuredContext, String userMessage, String fallbackDraft) {
        return fallbackDraft;
    }
}
