package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.rule.domain.RuleExecutionContext;
import com.example.disclosurereview.rule.domain.RuleExecutionResult;
import com.example.disclosurereview.rule.domain.RuleExecutorType;
import com.example.disclosurereview.rule.domain.RuleValidationResult;

import java.util.Map;

public interface RuleExecutor {

    RuleExecutorType supports();

    RuleValidationResult validate(ReviewRuleVersionEntity version);

    RuleExecutionResult execute(RuleExecutionContext context,
                                ReviewRuleDefinitionEntity definition,
                                ReviewRuleVersionEntity version);

    Map<String, Object> schema();
}
