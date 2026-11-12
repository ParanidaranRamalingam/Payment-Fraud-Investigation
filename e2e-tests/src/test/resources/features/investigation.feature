@ui @investigation
Feature: Investigation workspace and tool execution

  Scenario: Bounded tool plan executes and renders the timeline
    Given a case exists for scenario "txn_fraud_e2e1" with claim type "UNRECOGNISED_TRANSACTION"
    When I run the investigation
    Then the tool execution timeline should show runs
    And every planned tool should appear in the timeline

  Scenario: Customer claim is shown with provenance before investigation
    Given a case exists for scenario "txn_fraud_e2e2" with claim type "UNRECOGNISED_TRANSACTION"
    Then the customer claim should show provenance

  Scenario: Investigation cannot be re-run after completion
    Given a case exists for scenario "txn_fraud_e2e3" with claim type "UNRECOGNISED_TRANSACTION"
    And the case has been investigated
    Then the run-investigation action should not be available

  Scenario: Tool timeouts surface as structured unavailable evidence
    Given a case exists for scenario "txn_timeout_e2e1" with claim type "UNRECOGNISED_TRANSACTION"
    When I run the investigation
    Then the investigation should show unavailable or timed-out tools

  Scenario: Unavailable tools appear in the gaps panel
    Given a case exists for scenario "txn_unavail_e2e1" with claim type "UNRECOGNISED_TRANSACTION"
    When I run the investigation
    Then the gaps panel should list unresolved information
