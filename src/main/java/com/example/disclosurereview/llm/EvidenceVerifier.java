package com.example.disclosurereview.llm;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.Evidence;
import com.example.disclosurereview.model.ReviewIssue;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 证据回查：把模型返回的 evidenceText 映射回 PDF 页面文本，验证其真实性。
 * 模型不得凭空生成证据。
 */
@Component
public class EvidenceVerifier {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCT = Pattern.compile("[\\p{Punct}，。；：、（）【】《》“”‘’！？—…·]");

    /**
     * 校验 ReviewIssue 的证据是否真实存在于对应页文本。
     */
    public ReviewIssue verifyIssue(ReviewIssue issue, List<DocumentPage> pages) {
        if (issue == null) {
            return null;
        }
        boolean verified = verifyText(issue.pageNumber(), issue.evidenceText(), pages);
        return issue.withVerified(verified);
    }

    /**
     * 校验证据列表。
     */
    public List<Evidence> verifyEvidenceList(List<Evidence> evidence, List<DocumentPage> pages) {
        if (evidence == null) {
            return List.of();
        }
        return evidence.stream()
                .map(e -> e == null ? null : e.withVerified(verifyText(e.pageNumber(), e.text(), pages)))
                .toList();
    }

    /**
     * 根据 pageNumber 找到页面文本，规范化后检查 evidenceText 是否真实存在。
     */
    public boolean verifyText(Integer pageNumber, String evidenceText, List<DocumentPage> pages) {
        if (pageNumber == null || evidenceText == null || evidenceText.isBlank()) {
            return false;
        }
        Map<Integer, String> pageMap = new HashMap<>();
        for (DocumentPage p : pages) {
            pageMap.put(p.pageNumber(), p.normalizedText());
        }
        String pageText = pageMap.get(pageNumber);
        if (pageText == null) {
            return false;
        }
        String normalizedEvidence = normalizeForVerify(evidenceText);
        String normalizedPage = normalizeForVerify(pageText);
        return normalizedPage.contains(normalizedEvidence);
    }

    /**
     * 规范化：去空白、去标点（中英文），只做存在性校验，不做语义理解。
     */
    private String normalizeForVerify(String text) {
        if (text == null) {
            return "";
        }
        String t = SPACE.matcher(text).replaceAll("");
        t = PUNCT.matcher(t).replaceAll("");
        return t;
    }

    /**
     * 辅助：从 evidenceList 中找到第一个已验证的证据，用于给 FieldAssessment 提供页码。
     */
    public <T> T firstVerified(List<T> list, Function<T, Boolean> verifiedGetter) {
        if (list == null) {
            return null;
        }
        for (T item : list) {
            if (Boolean.TRUE.equals(verifiedGetter.apply(item))) {
                return item;
            }
        }
        return null;
    }
}
