package com.capstone.fraudcase.agent.common;

/**
 * Framework-neutral contract for answering analyst questions about one case, implemented by the deterministic
 * rules assistant and (later) an AI adapter. Implementations only describe the case-scoped facts they are given:
 * they never invoke tools, change case state, recall other cases or take any financial, account, compliance or
 * customer-contact action. Output is validated by the application before it is persisted or displayed.
 */
public interface CaseAssistant {

    CaseAssistantReply reply(CaseMessageContext context);
}
