package com.example.disclosurereview.rule;

import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.domain.RuleExecutionContext;
import com.example.disclosurereview.rule.domain.RuleExecutionResult;
import com.example.disclosurereview.rule.executor.EnumMappingRuleExecutor;
import com.example.disclosurereview.rule.executor.RuleJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnumMappingRuleExecutorTest {

    private final EnumMappingRuleExecutor executor =
            new EnumMappingRuleExecutor(new RuleJsonSupport(new ObjectMapper()));

    @Test
    void detectsRiskLevelLabelMappedToWrongCode() {
        String text = "示例理财甲产品按照风险程度从低到高分为五级，包括：低风险产品（R1）、中低风险产品（R2）、中低风险产品（R3）、中高风险产品（R4）、高风险产品（R5）。";
        RuleExecutionResult result = executor.execute(context(text), definition(), version());

        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).issueType()).isEqualTo(IssueType.CONTENT_LOGIC_CONFLICT);
        assertThat(result.issues().get(0).explanation()).contains("R3为中低风险，应为中风险");
    }

    @Test
    void doesNotReportCorrectRiskLevelMapping() {
        String text = "示例理财甲产品按照风险程度从低到高分为五级，包括：低风险产品（R1）、中低风险产品（R2）、中风险产品（R3）、中高风险产品（R4）、高风险产品（R5）。";
        RuleExecutionResult result = executor.execute(context(text), definition(), version());

        assertThat(result.issues()).isEmpty();
    }

    private RuleExecutionContext context(String text) {
        return new RuleExecutionContext(null, List.of(new DocumentPage(1, text, text)),
                "test.pdf", DocumentCategory.PROTOCOL, DocumentType.CUSTOMER_RIGHTS_NOTICE,
                "TEST001", "投资者权益须知", null, null);
    }

    private ReviewRuleDefinitionEntity definition() {
        ReviewRuleDefinitionEntity entity = new ReviewRuleDefinitionEntity();
        entity.setRuleCode(RuleReviewService.RULE_CONTENT_LOGIC_CONFLICT);
        entity.setRuleName("风险等级逻辑冲突");
        entity.setRuleType("ENUM_MAPPING");
        entity.setRuleCategory("HARD_CONFIG");
        entity.setEnabled(true);
        entity.setSeverity("HIGH");
        entity.setConfidence(1.0);
        entity.setVersionCode("CONTENT_LOGIC_CONFLICT:v1");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ReviewRuleVersionEntity version() {
        ReviewRuleVersionEntity entity = new ReviewRuleVersionEntity();
        entity.setExecutorType("ENUM_MAPPING");
        entity.setConditionJson("""
                {
                  "headerPattern":"(?:风险程度|风险等级)[^.;。；]{0,120}(?:从低到高|由低到高)(?:分为|包括)五级",
                  "entryPattern":"(低风险|中低风险|中风险|中高风险|高风险)产品?\\\\(R([1-5])\\\\)",
                  "labelGroup":1,
                  "codeGroup":2,
                  "expectedMapping":{"R1":"低风险","R2":"中低风险","R3":"中风险","R4":"中高风险","R5":"高风险"},
                  "checkDuplicates":true,
                  "checkMissing":true,
                  "checkOrder":true
                }
                """);
        entity.setActionJson("""
                {
                  "issueType":"CONTENT_LOGIC_CONFLICT",
                  "severity":"HIGH",
                  "confidence":1.0,
                  "source":"RULE",
                  "explanationTemplate":"正文枚举编号与名称映射存在逻辑冲突：${detail}",
                  "suggestionTemplate":"请人工核对风险等级定义。"
                }
                """);
        return entity;
    }
}
