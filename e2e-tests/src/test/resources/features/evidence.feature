@ui @evidence
Feature: Evidence workspace

  Scenario: Evidence cards show type, status and provenance
    Given a case exists for scenario "txn_fraud_e2e5" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    When I open the evidence workspace
    Then evidence cards should be displayed with provenance

  Scenario: Unavailable evidence is flagged distinctly
    Given a case exists for scenario "txn_unavail_e2e2" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    When I open the evidence workspace
    Then unavailable evidence should be flagged
