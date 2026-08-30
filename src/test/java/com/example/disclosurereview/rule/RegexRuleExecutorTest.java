package com.example.disclosurereview.rule;

import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.executor.RegexRuleExecutor;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexRuleExecutorTest {

    private final RegexRuleExecutor executor =
            new RegexRuleExecutor(new RuleJsonSupport(new ObjectMapper()));

    @Test
    void rejectsPatternLongerThanConfiguredLimit() {
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        version.setConditionJson("""
                {"pattern":"aaaaaaaaaaaaaaaaaaaa","maxPatternLength":10}
                """);

        assertThat(executor.validate(version).valid()).isFalse();
    }

    @Test
    void rejectsUnsupportedRe2jSyntax() {
        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        version.setConditionJson("""
                {"pattern":"(a)\\\\1","maxPatternLength":100}
                """);

        assertThat(executor.validate(version).valid()).isFalse();
    }
}
