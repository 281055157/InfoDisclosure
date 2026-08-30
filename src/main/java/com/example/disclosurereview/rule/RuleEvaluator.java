package com.example.disclosurereview.rule;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.Product;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RuleEvaluator {

    private final RuleReviewService ruleReviewService;

    public RuleEvaluator(RuleReviewService ruleReviewService) {
        this.ruleReviewService = ruleReviewService;
    }

    public RuleReviewService.RuleReviewOutcome evaluate(List<DocumentPage> pages,
                                                        DocumentType documentType,
                                                        String declaredProductCode,
                                                        Product targetProduct,
                                                        Set<String> enabledRuleCodes) {
        return ruleReviewService.review(pages, documentType, declaredProductCode, targetProduct, enabledRuleCodes);
    }
}
