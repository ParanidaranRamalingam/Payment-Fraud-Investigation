package com.capstone.fraudcase.e2e.hooks;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;

import java.util.concurrent.atomic.AtomicReference;

/** Tracks the running pickle step so the AfterStep hook can label screenshots. */
public class StepTracker implements ConcurrentEventListener {

    private static final AtomicReference<String> CURRENT = new AtomicReference<>("step");

    public static String currentStep() {
        return CURRENT.get();
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestStepStarted.class, e -> {
            if (e.getTestStep() instanceof PickleStepTestStep step) {
                CURRENT.set(step.getStep().getKeyword() + step.getStepText());
            }
        });
        publisher.registerHandlerFor(TestStepFinished.class, e -> {
            if (e.getTestStep() instanceof PickleStepTestStep step) {
                CURRENT.set(step.getStep().getKeyword() + step.getStepText());
            }
        });
    }
}
