package com.haolo.ticketing.ai;

/** Hexagonal Port for AI generation — supports external LLM adapters with deterministic local fallback. */
public interface AiAssistantPort {
    String adapterName();
    boolean isAvailable();
    String enhanceResponse(String systemPrompt, String structuredContext, String userMessage, String fallbackDraft);
}
