@ui @citations
Feature: Policy citations page

  Scenario: Grounded cases list policy citations with passages
    Given I am signed in as an analyst
    When I start a new case
    And I fill a valid intake for scenario "txn_fraud_e2e6" with claim type "UNRECOGNISED_TRANSACTION"
    And I set intake field "customerStatement" to "unauthorized transaction card compromised new device unrecognized merchant fraud cardholder report"
    And I submit the intake expecting a case
    And the case has been investigated
    When I open the policy citations page
    Then policy citations should be listed

  Scenario: Un-grounded cases show an explicit abstention state
    Given an investigated case exists for scenario "txn_missing_e2e1" with claim type "OTHER"
    When I open the policy citations page
    Then the abstention or empty state should be shown
