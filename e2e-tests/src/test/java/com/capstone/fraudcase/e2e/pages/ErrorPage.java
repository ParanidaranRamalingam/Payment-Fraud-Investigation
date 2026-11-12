package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;

public class ErrorPage extends BasePage {

    public ErrorPage(TestContext context) {
        super(context);
    }

    public boolean shows(String text) {
        return pageText().contains(text);
    }

    public int statusCode() {
        // Selenium cannot read the HTTP status directly; the error template carries the detail.
        return pageText().contains("not found") ? 404 : 500;
    }
}
