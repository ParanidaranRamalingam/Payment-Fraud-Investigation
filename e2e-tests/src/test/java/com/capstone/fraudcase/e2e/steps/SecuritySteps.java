package com.capstone.fraudcase.e2e.steps;

import com.capstone.fraudcase.e2e.config.TestContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecuritySteps {

    private final TestContext context;

    public SecuritySteps(TestContext context) {
        this.context = context;
    }

    @Then("the login error {string} should be shown")
    public void loginError(String expected) {
        com.capstone.fraudcase.e2e.pages.LoginPage page = context.get("loginPage");
        assertTrue(page.errorMessage().contains(expected));
    }

    @Then("the sign-out confirmation should be shown")
    public void signOutConfirm() {
        assertTrue(new com.capstone.fraudcase.e2e.pages.LoginPage(context).logoutMessage()
                .toLowerCase().contains("signed out"));
    }

    @Then("access should be denied")
    public void accessDenied() {
        String body = context.driver().getPageSource();
        assertTrue(body.contains("403") || body.toLowerCase().contains("denied")
                || body.toLowerCase().contains("forbidden"),
                "expected a 403 page");
    }

    /** POSTs to a protected UI endpoint with the browser's session cookie but no CSRF token. */
    @When("I post to intake without a CSRF token")
    public void postWithoutCsrf() throws Exception {
        var cookies = context.driver().manage().getCookieNamed("JSESSIONID");
        org.junit.jupiter.api.Assertions.assertNotNull(cookies, "no session cookie — sign in first");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(context.baseUrl() + "/ui/cases"))
                .header("Cookie", "JSESSIONID=" + cookies.getValue())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("customerReference=x")).build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        context.put("csrfStatus", res.statusCode());
    }

    @Then("the request should be rejected with status {int}")
    public void statusIs(int expected) {
        assertEquals(expected, (int) context.get("csrfStatus"));
    }

    @Then("the login page should be shown")
    public void loginPageShown() {
        List<org.openqa.selenium.WebElement> fields = context.driver()
                .findElements(org.openqa.selenium.By.id("username"));
        assertTrue(!fields.isEmpty()
                || java.net.URI.create(context.driver().getCurrentUrl()).getPath().equals("/login"));
    }
}
