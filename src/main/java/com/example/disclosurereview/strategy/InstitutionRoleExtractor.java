package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.AgencyAssessment;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.Evidence;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** 识别代销协议中目标机构的代理销售方、代销机构或销售机构角色。 */
@Component
public class InstitutionRoleExtractor {

    private static final List<String> ROLE_KEYWORDS = List.of(
            "代理销售方", "代理销售机构", "代销机构", "销售机构", "代销", "代理销售", "乙方");

    private static final List<String> WEAK_CONTEXT_WORDS = List.of(
            "账户", "账号", "开户行", "户名", "地址", "邮编", "电话", "传真", "联系人");

    public AgencyAssessment assess(List<DocumentPage> pages,
                                   List<String> targetBankNames,
                                   boolean distributionAgreement) {
        if (pages == null || targetBankNames == null || targetBankNames.isEmpty()) {
            return AgencyAssessment.empty(distributionAgreement);
        }
        for (DocumentPage page : pages) {
            String text = page.normalizedText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            for (String bank : targetBankNames) {
                if (!StringUtils.hasText(bank)) {
                    continue;
                }
                int idx = text.indexOf(bank);
                while (idx >= 0) {
                    String context = context(text, idx, idx + bank.length(), 80);
                    String role = roleIn(context);
                    if (role != null && !onlyWeakContext(context)) {
                        return new AgencyAssessment(distributionAgreement, true, bank, role, 0.9,
                                List.of(new Evidence(page.pageNumber(), context, true)));
                    }
                    idx = text.indexOf(bank, idx + bank.length());
                }
            }
        }
        return AgencyAssessment.empty(distributionAgreement);
    }

    private String roleIn(String context) {
        for (String role : ROLE_KEYWORDS) {
            if (context.contains(role)) {
                return role;
            }
        }
        return null;
    }

    private boolean onlyWeakContext(String context) {
        boolean weak = WEAK_CONTEXT_WORDS.stream().anyMatch(context::contains);
        boolean role = ROLE_KEYWORDS.stream().anyMatch(context::contains);
        return weak && !role;
    }

    private String context(String text, int start, int end, int radius) {
        int from = Math.max(0, start - radius);
        int to = Math.min(text.length(), end + radius);
        return text.substring(from, to).replaceAll("\\s+", " ").strip();
    }
}
