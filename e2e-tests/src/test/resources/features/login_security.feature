@security @ui
Feature: Login, session and role-based access control

  Scenario: Analyst signs in and lands on the case dashboard
    When I sign in as "it-analyst" with password "analyst-test-pw"
    Then I should be on the dashboard

  Scenario: Reviewer signs in and lands on the case dashboard
    When I sign in as "it-reviewer" with password "reviewer-test-pw"
    Then I should be on the dashboard

  Scenario: Invalid credentials are rejected
    When I try to sign in as "it-analyst" with password "wrong-password"
    Then the login error "Invalid username or password" should be shown

  Scenario: Unauthenticated users are redirected to login
    When I open the page "/ui"
    Then the login page should be shown

  Scenario: Signing out ends the session
    Given I am signed in as an analyst
    When I sign out
    Then the sign-out confirmation should be shown
    When I open the page "/ui"
    Then the login page should be shown

  Scenario: Reviewer cannot submit a case through the UI
    Given I am signed in as a reviewer
    When I open the page "/ui/cases/new"
    And I post to intake without a CSRF token
    Then the request should be rejected with status 403

  Scenario: CSRF-protected form posts reject tokenless requests
    Given I am signed in as an analyst
    When I post to intake without a CSRF token
    Then the request should be rejected with status 403
