package com.example.disclosurereview.governance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class GovernancePostgresMigrationIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("disclosure_review")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private JdbcTemplate jdbc;

    @Test
    void flywayCreatesGovernanceTablesAndRuleScopedVersionConstraint() {
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name in (
                  'rule_governance_run', 'rule_feedback_governance_group', 'rule_change_proposal',
                  'rule_governance_memory', 'rule_governance_tool_call', 'rule_governance_event',
                  'rule_governance_trace_span')
                """, Integer.class);
        assertThat(tables).isEqualTo(7);
        Integer migration = jdbc.queryForObject("select max(installed_rank) from flyway_schema_history where success", Integer.class);
        assertThat(migration).isGreaterThanOrEqualTo(10);
        Integer runColumns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'rule_governance_run'
                  and column_name in ('skipped_feedback_count', 'skip_reason_summary', 'trace_id')
                """, Integer.class);
        assertThat(runColumns).isEqualTo(3);
        Integer intentColumn = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'rule_feedback_governance_group'
                  and column_name = 'governance_intent' and is_nullable = 'NO'
                """, Integer.class);
        assertThat(intentColumn).isEqualTo(1);
        Integer nullableSourceColumns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'rule_feedback_governance_group'
                  and column_name in ('rule_code', 'rule_version_id', 'rule_version') and is_nullable = 'YES'
                """, Integer.class);
        assertThat(nullableSourceColumns).isEqualTo(3);
        Integer versionIndex = jdbc.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = 'public' and indexname = 'uk_review_rule_version_rule_version_code'
                """, Integer.class);
        assertThat(versionIndex).isEqualTo(1);
    }
}
