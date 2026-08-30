package com.example.disclosurereview.governance.service;

import com.example.disclosurereview.persistence.entity.ReviewRuleFeedbackEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class GovernanceGroupKeyService {
    public String key(ReviewRuleFeedbackEntity feedback) {
        if (feedback == null) throw new IllegalArgumentException("feedback is required");
        if ("FALSE_NEGATIVE".equalsIgnoreCase(feedback.getFeedbackType())) {
            return String.join("|",
                    "RULE_GAP",
                    normalized(issueType(feedback)),
                    normalized(feedback.getDocumentCategory()),
                    normalized(feedback.getDeclaredDocumentType()),
                    normalized(feedback.getFeedbackType()));
        }
        return String.join("|",
                normalized(feedback.getRuleCode()),
                normalized(feedback.getRuleVersionId()),
                normalized(feedback.getDocumentCategory()),
                normalized(feedback.getDeclaredDocumentType()),
                normalized(feedback.getFeedbackType()));
    }

    public String issueType(ReviewRuleFeedbackEntity feedback) {
        if (feedback == null) return null;
        if (feedback.getIssue() != null && StringUtils.hasText(feedback.getIssue().getIssueCode())) {
            return feedback.getIssue().getIssueCode();
        }
        return feedback.getRuleCode();
    }

    private String normalized(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) return "UNKNOWN";
        return String.valueOf(value).strip().toUpperCase(Locale.ROOT).replace('|', '_');
    }
}
