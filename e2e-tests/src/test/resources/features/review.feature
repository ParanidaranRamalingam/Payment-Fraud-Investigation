@ui @review
Feature: Human review disposition

  Scenario: Analyst sees a read-only review form
    Given a case exists for scenario "txn_fraud_e2e7" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    When I open the review page
    Then the review form should not be editable

  Scenario: Reviewer accepts the recommendation and the case is routed
    Given a case exists for scenario "txn_fraud_e2e8" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    And I am signed in as a reviewer
    When I open the review page
    Then the review form should be visible
    When I choose decision "ACCEPT"
    And I enter rationale "Evidence supports the recommended fraud routing."
    And I acknowledge the missing information
    And I submit the review
    Then the review should be recorded
    And the case status should be "ROUTED"

  Scenario: Override without rationale is blocked
    Given a case exists for scenario "txn_fraud_e2e9" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    And I am signed in as a reviewer
    When I open the review page
    And I choose decision "OVERRIDE"
    And I set final classification "GENUINE_TRANSACTION" and final route "MANUAL_REVIEW"
    And I submit the review bypassing client-side required fields
    Then the review should be rejected with a validation error

  Scenario: Override requires final classification and route
    Given a case exists for scenario "txn_fraud_e2ea" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    And I am signed in as a reviewer
    When I open the review page
    And I choose decision "OVERRIDE"
    And I enter rationale "Overriding based on analyst knowledge."
    And I submit the review
    Then the review should be rejected with a validation error

  Scenario: Closing a case records the disposition
    Given a case exists for scenario "txn_genuine_e2e5" with claim type "OTHER"
    And the case has been investigated
    And I am signed in as a reviewer
    When I open the review page
    And I choose decision "CLOSE"
    And I enter rationale "No fraud indicators; closing without routing."
    And I acknowledge the missing information
    And I submit the review
    Then the review should be recorded
    And the case status should be "CLOSED"
