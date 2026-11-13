@ui @recommendation
Feature: Advisory recommendation page

  Scenario: Recommendation is advisory and requires human review
    Given an investigated case exists for scenario "txn_fraud_e2e4" with claim type "UNRECOGNISED_TRANSACTION"
    When I open the recommendation page
    Then the advisory assessment should be displayed
    And human review should be marked required
    And the confidence should be visualised

  Scenario: Conflicting signals list missing or contradictory information
    Given an investigated case exists for scenario "txn_conflict_e2e1" with claim type "UNRECOGNISED_TRANSACTION"
    When I open the recommendation page
    Then the advisory assessment should be displayed
    And the recommendation should list missing or contradictory information

  Scenario: Partial evidence still produces an advisory assessment
    Given an investigated case exists for scenario "txn_partial_e2e1" with claim type "UNRECOGNISED_TRANSACTION"
    When I open the recommendation page
    Then the advisory assessment should be displayed
    And human review should be marked required
