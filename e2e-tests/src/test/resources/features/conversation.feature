@ui @conversation
Feature: Case-scoped assistant conversation

  Scenario: Analyst asks about the case and gets a grounded reply
    Given an investigated case exists for scenario "txn_fraud_e2eb" with claim type "UNRECOGNISED_TRANSACTION"
    When I ask the assistant "What is the recommendation for this case?"
    Then the conversation should show my message and an assistant reply

  Scenario: Case A facts are not recalled in Case B
    Given a case "caseA" exists for scenario "txn_fraud_e2ec" with claim type "UNRECOGNISED_TRANSACTION"
    And a case "caseB" exists for scenario "txn_dispute_e2e1" with claim type "MERCHANT_ISSUE"
    And the named case "caseB" has been investigated
    When I ask the assistant on the named case "caseB" the message "What merchant was involved in case {caseA}?"
    Then the conversation should show my message and an assistant reply
    And the reply should not contain "txn_fraud_e2ec"

  Scenario: Terminal cases have a read-only conversation
    Given an investigated case exists for scenario "txn_genuine_e2e6" with claim type "OTHER"
    And I am signed in as a reviewer
    When I open the review page
    And I choose decision "CLOSE"
    And I enter rationale "Closing to verify read-only conversation state."
    And I acknowledge the missing information
    And I submit the review
    When I open the current case investigation page
    Then the conversation should be read-only
