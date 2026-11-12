package com.capstone.fraudcase.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlRepositoryTest
class SchemaMigrationMySqlTest {

    private static final List<String> DOMAIN_TABLES = List.of("fraud_case", "transaction_claim", "tool_trace",
            "evidence_item", "recommendation", "recommendation_evidence", "recommendation_missing_info",
            "recommendation_trace_ref", "policy_citation", "review_disposition", "audit_event", "case_message",
            "case_message_evidence");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flyway_cleanSchema_appliesAllMigrationsInOrder() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).extracting(r -> r.get("version")).containsExactly("1", "2", "3");
        assertThat(history).allSatisfy(r -> assertThat(r.get("success")).isEqualTo(Boolean.TRUE));
    }

    @Test
    void v2_evidenceProvenanceColumns_existAsNullableTextTypes() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name AS name, data_type AS type, character_maximum_length AS len, is_nullable AS nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'evidence_item'
                  AND column_name IN ('source', 'content_hash', 'payload')
                ORDER BY column_name""");

        assertThat(columns).extracting(c -> c.get("name") + ":" + c.get("type") + ":" + c.get("nullable"))
                .containsExactly("content_hash:varchar:YES", "payload:text:YES", "source:varchar:YES");
    }

    @Test
    void v2_indexesAndChecks_exist() {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = DATABASE()""",
                String.class);
        List<String> checks = jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'evidence_item' AND constraint_type = 'CHECK'""",
                String.class);

        assertThat(indexes).contains("ix_evidence_item_content_hash", "ix_tool_trace_case_tool");
        assertThat(checks).contains("ck_evidence_item_payload_available", "ck_evidence_item_payload_pair");
    }

    @Test
    void database_isMySql8() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.");
    }

    @Test
    void tables_allDomainTables_useInnoDbAndUtf8mb4() {
        List<Map<String, Object>> tables = jdbc.queryForList("""
                SELECT table_name AS name, engine, table_collation AS collation
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'""");

        assertThat(tables).extracting(t -> t.get("name")).containsExactlyInAnyOrderElementsOf(DOMAIN_TABLES);
        assertThat(tables).allSatisfy(t -> {
            assertThat(t.get("engine")).isEqualTo("InnoDB");
            assertThat((String) t.get("collation")).startsWith("utf8mb4");
        });
    }

    @Test
    void columns_monetaryAndScoreValues_useDecimalNeverFloat() {
        Map<String, Object> amount = jdbc.queryForMap("""
                SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'transaction_claim' AND column_name = 'amount'""");
        Integer floatColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND data_type IN ('float', 'double', 'real')""", Integer.class);

        assertThat(amount).containsEntry("DATA_TYPE", "decimal");
        assertThat(((Number) amount.get("NUMERIC_PRECISION")).intValue()).isEqualTo(19);
        assertThat(((Number) amount.get("NUMERIC_SCALE")).intValue()).isEqualTo(2);
        assertThat(floatColumns).isZero();
    }

    @Test
    void columns_timestamps_useDatetimeWithMicroseconds() {
        List<Map<String, Object>> timestamps = jdbc.queryForList("""
                SELECT table_name, column_name, data_type, datetime_precision FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                  AND (column_name LIKE '%\\_at' OR data_type IN ('datetime', 'timestamp'))""");

        assertThat(timestamps).isNotEmpty().allSatisfy(c -> {
            assertThat(c.get("DATA_TYPE")).isEqualTo("datetime");
            assertThat(((Number) c.get("DATETIME_PRECISION")).intValue()).isEqualTo(6);
        });
    }

    @Test
    void columns_enumValues_storedAsVarcharNotNativeEnum() {
        Integer nativeEnums = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND data_type = 'enum'""", Integer.class);

        assertThat(nativeEnums).isZero();
    }

    @Test
    void foreignKeys_childTables_referenceParents() {
        List<String> fks = jdbc.queryForList("""
                SELECT CONCAT(table_name, '.', column_name, '->', referenced_table_name)
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL""", String.class);

        assertThat(fks).contains(
                "transaction_claim.case_id->fraud_case",
                "tool_trace.case_id->fraud_case",
                "evidence_item.case_id->fraud_case",
                "evidence_item.tool_trace_id->tool_trace",
                "recommendation.case_id->fraud_case",
                "recommendation_evidence.recommendation_id->recommendation",
                "recommendation_evidence.evidence_item_id->evidence_item",
                "policy_citation.recommendation_id->recommendation",
                "review_disposition.case_id->fraud_case",
                "review_disposition.recommendation_id->recommendation",
                "audit_event.case_id->fraud_case",
                "case_message.case_id->fraud_case",
                "case_message.reply_to_id->case_message",
                "case_message_evidence.message_id->case_message",
                "case_message_evidence.evidence_item_id->evidence_item");
    }

    @Test
    void indexes_caseStatusAndTimestampLookups_exist() {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics
                WHERE table_schema = DATABASE()""", String.class);

        assertThat(indexes).contains("ix_fraud_case_status_created", "ix_fraud_case_created_at",
                "ix_evidence_item_case_collected", "ix_tool_trace_case_started", "ix_recommendation_case_created",
                "ix_review_disposition_case_reviewed", "ix_audit_event_case_occurred", "uq_transaction_claim_case");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "INSERT INTO fraud_case VALUES ('c1','NEW','cus_abcdef',NULL,'s','a',TRUE,FALSE,NOW(6),NOW(6),0)|ck_fraud_case_human_review",
            "INSERT INTO fraud_case VALUES ('c2','NEW','cus_abcdef',NULL,'s','a',FALSE,TRUE,NOW(6),NOW(6),0)|ck_fraud_case_synthetic",
            "INSERT INTO fraud_case VALUES ('c3','APPROVED','cus_abcdef',NULL,'s','a',TRUE,TRUE,NOW(6),NOW(6),0)|ck_fraud_case_status"})
    void checkConstraints_humanReviewSyntheticAndStatus_enforcedByDatabase(String sql, String constraint) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataAccessException.class).hasMessageContaining(constraint);
    }

    @Test
    void checkConstraint_nonPositiveAmount_rejectedByDatabase() {
        jdbc.update("INSERT INTO fraud_case VALUES ('c4','NEW','cus_abcdef',NULL,'s','a',TRUE,TRUE,NOW(6),NOW(6),0)");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transaction_claim VALUES
                ('t1','c4','txn_000001','M',NULL,0.00,'GBP',NOW(6),'OTHER',NOW(6))"""))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_transaction_claim_amount");
    }

    @Test
    void checkConstraint_overrideWithoutFinalOutcome_rejectedByDatabase() {
        jdbc.update("INSERT INTO fraud_case VALUES ('c5','NEW','cus_abcdef',NULL,'s','a',TRUE,TRUE,NOW(6),NOW(6),0)");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO review_disposition VALUES
                ('r1','c5',NULL,'analyst-1','OVERRIDE',NULL,NULL,'Long enough rationale',NOW(6))"""))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_review_disposition_override");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "INSERT INTO case_message VALUES ('m1','c6','ASSISTANT','system','reply',NULL,'RULES',NOW(6))|ck_case_message_reply_to",
            "INSERT INTO case_message VALUES ('m2','c6','ANALYST','analyst-1','question',NULL,'RULES',NOW(6))|ck_case_message_processor",
            "INSERT INTO case_message VALUES ('m3','c6','SYSTEM','analyst-1','question',NULL,NULL,NOW(6))|ck_case_message_role",
            "INSERT INTO case_message VALUES ('m4','c6','ANALYST','analyst-1','   ',NULL,NULL,NOW(6))|ck_case_message_content"})
    void v3_caseMessageChecks_enforcedByDatabase(String sql, String constraint) {
        jdbc.update("INSERT INTO fraud_case VALUES ('c6','NEW','cus_abcdef',NULL,'s','a',TRUE,TRUE,NOW(6),NOW(6),0)");

        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataAccessException.class).hasMessageContaining(constraint);
    }

    @Test
    void v3_caseMessageIndex_exists() {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = DATABASE()""",
                String.class);

        assertThat(indexes).contains("ix_case_message_case_created", "uq_case_message_evidence");
    }
}
