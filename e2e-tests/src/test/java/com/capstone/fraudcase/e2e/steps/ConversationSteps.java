package com.capstone.fraudcase.e2e.steps;

import com.capstone.fraudcase.e2e.config.TestContext;
import com.capstone.fraudcase.e2e.pages.InvestigationPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversationSteps {

    private final TestContext context;

    public ConversationSteps(TestContext context) {
        this.context = context;
    }

    @When("I ask the assistant {string}")
    public void ask(String message) {
        new InvestigationPage(context).sendMessage(message);
    }

    @When("I ask the assistant on the named case {string} the message {string}")
    public void askOnCase(String key, String message) {
        context.caseId(context.namedCaseId(key)); // remember which case the conversation is on
        new InvestigationPage(context).open(context.namedCaseId(key))
                .sendMessage(substitute(message));
    }

    /** Replaces {key} placeholders with stored named case ids. */
    private String substitute(String text) {
        for (String key : new String[]{"caseA", "caseB"}) {
            Object v = context.get(key);
            if (v != null) {
                text = text.replace("{" + key + "}", v.toString());
            }
        }
        return text;
    }

    @Then("the conversation should show my message and an assistant reply")
    public void replyShown() {
        var messages = new InvestigationPage(context).messages();
        assertTrue(messages.size() >= 2, "expected analyst + assistant messages, got " + messages);
    }

    @Then("the reply should not contain {string}")
    public void replyNotContains(String text) {
        var messages = new InvestigationPage(context).messages();
        String all = String.join("\n", messages);
        assertFalse(all.contains(text), "leaked cross-case content: " + all);
    }

    @Then("the conversation should be read-only")
    public void readOnly() {
        assertTrue(new InvestigationPage(context).conversationReadOnly());
    }
}
