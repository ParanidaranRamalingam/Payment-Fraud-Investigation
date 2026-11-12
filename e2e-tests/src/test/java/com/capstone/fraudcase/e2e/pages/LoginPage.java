package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    private static final By USERNAME = By.id("username");
    private static final By PASSWORD = By.id("password");
    private static final By SUBMIT = By.cssSelector("button[type='submit']");
    private static final By ERROR = By.cssSelector(".alert--danger");
    private static final By LOGOUT_MSG = By.cssSelector(".alert--ok");

    public LoginPage(TestContext context) {
        super(context);
    }

    public LoginPage open() {
        go("/login");
        return this;
    }

    public DashboardPage signIn(String username, String password) {
        type(USERNAME, username);
        type(PASSWORD, password);
        click(SUBMIT);
        return new DashboardPage(context);
    }

    public LoginPage signInExpectingFailure(String username, String password) {
        type(USERNAME, username);
        type(PASSWORD, password);
        click(SUBMIT);
        return this;
    }

    public String errorMessage() {
        return waitVisible(ERROR).getText();
    }

    public String logoutMessage() {
        return waitVisible(LOGOUT_MSG).getText();
    }

    public boolean isAt() {
        return !all(USERNAME).isEmpty() && currentPath().equals("/login");
    }
}
