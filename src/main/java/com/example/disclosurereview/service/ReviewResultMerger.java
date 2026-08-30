package com.example.disclosurereview.service;

import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.IssueType;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.Severity;
import com.example.disclosurereview.model.TargetMatchDecision;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 合并规则结果与模型结果，计算最终业务风险等级。
 */
@Service
public class ReviewResultMerger {

    private final DocumentTypeAliasResolver documentTypeResolver;

    public ReviewResultMerger(DocumentTypeAliasResolver documentTypeResolver) {
        this.documentTypeResolver = documentTypeResolver;
    }

    /**
     * 按风险合并规则计算最终风险。
     */
    public BusinessRisk mergeRisk(ReviewResult reviewResult) {
        if (reviewResult == null) {
            return BusinessRisk.UNKNOWN;
        }
        BusinessRisk assessmentRisk = riskFromTargetAssessment(reviewResult.targetProductAssessment());
        List<ReviewIssue> allIssues = new ArrayList<>();
        if (reviewResult.ruleResult() != null && reviewResult.ruleResult().issues() != null) {
            allIssues.addAll(reviewResult.ruleResult().issues());
        }
        if (reviewResult.llmResult() != null && reviewResult.llmResult().issues() != null) {
            allIssues.addAll(reviewResult.llmResult().issues());
        }

        // HIGH 条件
        if (assessmentRisk == BusinessRisk.HIGH) {
            return BusinessRisk.HIGH;
        }
        if (hasHighRisk(reviewResult, allIssues)) {
            return BusinessRisk.HIGH;
        }
        // MEDIUM 条件
        if (assessmentRisk == BusinessRisk.MEDIUM) {
            return BusinessRisk.MEDIUM;
        }
        if (hasMediumRisk(reviewResult, allIssues)) {
            return BusinessRisk.MEDIUM;
        }
        // LOW 条件
        if (assessmentRisk == BusinessRisk.LOW) {
            return BusinessRisk.LOW;
        }
        if (hasLowRisk(reviewResult, allIssues)) {
            return BusinessRisk.LOW;
        }
        // 没有明确问题
        if (allIssues.isEmpty() && assessmentRisk != BusinessRisk.UNKNOWN) {
            return assessmentRisk;
        }
        if (allIssues.isEmpty()) {
            return BusinessRisk.NORMAL;
        }
        return BusinessRisk.UNKNOWN;
    }

    private boolean hasHighRisk(ReviewResult r, List<ReviewIssue> issues) {
        // 1. 文件名产品代码与正文主要产品代码不同，且正文代码在产品库中匹配另一产品
        if (!targetAssessmentAcceptsProduct(r)
                && StringUtils.hasText(r.declaredInfo() != null ? r.declaredInfo().productCode() : null)
                && r.llmResult() != null
                && r.llmResult().mainProductCode() != null
                && StringUtils.hasText(r.llmResult().mainProductCode().value())
                && !r.declaredInfo().productCode().equals(r.llmResult().mainProductCode().value())
                && r.productMaster() != null
                && r.productMaster().matched()
                && r.llmResult().mainProductCode().value().equals(r.productMaster().productCode())) {
            return true;
        }
        // 2. 正文不同页面出现两个明确且互相冲突的产品代码（规则检测）
        long conflictCount = issues.stream()
                .filter(i -> i.issueType() == IssueType.CONTENT_PRODUCT_CODE_CONFLICT
                        || i.issueType() == IssueType.CONTENT_LOGIC_CONFLICT)
                .filter(i -> i.severity() == Severity.HIGH || (i.confidence() != null && i.confidence() >= 0.85))
                .count();
        if (conflictCount > 0) {
            return true;
        }
        // 3. 大模型发现疑似模板残留，confidence >= 0.85，且证据已回查成功
        return issues.stream()
                .anyMatch(i -> i.issueType() == IssueType.POSSIBLE_TEMPLATE_RESIDUE
                        && i.confidence() != null && i.confidence() >= 0.85
                        && Boolean.TRUE.equals(i.verified()));
    }

    private boolean hasMediumRisk(ReviewResult r, List<ReviewIssue> issues) {
        // 1. 声明文件类型与正文候选文件类型不一致，且类型置信度 >= 0.8
        if (r.declaredInfo() != null
                && StringUtils.hasText(r.declaredInfo().documentType())
                && r.llmResult() != null
                && r.llmResult().candidateDocumentType() != null
                && StringUtils.hasText(r.llmResult().candidateDocumentType().value())
                && !sameType(r.declaredInfo().documentType(), r.llmResult().candidateDocumentType().value())
                && r.llmResult().candidateDocumentType().confidence() != null
                && r.llmResult().candidateDocumentType().confidence() >= 0.8) {
            return true;
        }
        // 2. 正文产品名称与产品库名称疑似不一致
        if (r.productMaster() != null && r.productMaster().matched()
                && r.llmResult() != null
                && r.llmResult().mainProductName() != null
                && StringUtils.hasText(r.llmResult().mainProductName().value())
                && StringUtils.hasText(r.productMaster().productName())
                && !targetAssessmentAcceptsProduct(r)
                && !nameAllowedByMaster(r.llmResult().mainProductName().value(), r.productMaster())) {
            return true;
        }
        // 3. 声明产品代码未在产品库中找到，需要人工确认代码或补充主数据。
        if (issues.stream().anyMatch(i -> i.issueType() == IssueType.DECLARED_PRODUCT_NOT_FOUND)) {
            return true;
        }
        // 4. 大模型发现冲突，但置信度在 0.6 到 0.85 之间
        return issues.stream()
                .anyMatch(i -> i.issueType() == IssueType.CONTENT_PRODUCT_CODE_CONFLICT
                        && i.confidence() != null
                        && i.confidence() >= 0.6
                        && i.confidence() < 0.85);
    }

    private boolean hasLowRisk(ReviewResult r, List<ReviewIssue> issues) {
        // 1. 产品名称可能只是简称
        boolean variant = issues.stream()
                .anyMatch(i -> i.issueType() == IssueType.PRODUCT_NAME_VARIANT);
        if (variant) {
            return true;
        }
        // 2. 只存在弱提示 / LOW severity
        boolean lowSeverity = issues.stream()
                .anyMatch(i -> i.severity() == Severity.LOW);
        if (lowSeverity) {
            return true;
        }
        // 3. 发现其他产品引用，但模型认为可能是正常引用
        return r.llmResult() != null
                && r.llmResult().otherProductReferences() != null
                && r.llmResult().otherProductReferences().stream()
                .anyMatch(p -> p.assessment() == IssueType.PRODUCT_REFERENCE);
    }

    /**
     * 合并所有问题列表，去重、排序。
     */
    public List<ReviewIssue> mergeIssues(ReviewResult reviewResult) {
        List<ReviewIssue> all = new ArrayList<>();
        if (reviewResult.ruleResult() != null && reviewResult.ruleResult().issues() != null) {
            all.addAll(reviewResult.ruleResult().issues());
        }
        if (reviewResult.llmResult() != null && reviewResult.llmResult().issues() != null) {
            all.addAll(reviewResult.llmResult().issues());
        }
        ReviewIssue typeMismatch = declaredTypeMismatchIssue(reviewResult);
        if (typeMismatch != null) {
            all.add(typeMismatch);
        }
        // 按 issueType + pageNumber + evidenceText 去重
        Map<String, ReviewIssue> distinct = all.stream()
                .collect(Collectors.toMap(
                        i -> i.issueType() + "|" + i.pageNumber() + "|" + i.evidenceText(),
                        Function.identity(),
                        this::strongerIssue));
        List<ReviewIssue> list = new ArrayList<>(distinct.values());
        // 按 severity 排序
        list.sort(Comparator.comparing((ReviewIssue i) -> severityRank(i.severity()))
                .thenComparing(i -> i.pageNumber() != null ? i.pageNumber() : Integer.MAX_VALUE));
        return list;
    }

    public BusinessAcceptanceDecision mergeBusinessAcceptance(ReviewResult reviewResult, BusinessRisk risk) {
        BusinessAcceptanceDecision targetDecision = reviewResult == null
                || reviewResult.targetProductAssessment() == null
                || reviewResult.targetProductAssessment().businessAcceptanceDecision() == null
                ? BusinessAcceptanceDecision.UNKNOWN
                : reviewResult.targetProductAssessment().businessAcceptanceDecision();
        if (targetDecision == BusinessAcceptanceDecision.REJECT_SUGGESTED
                || targetDecision == BusinessAcceptanceDecision.MANUAL_REVIEW) {
            return targetDecision;
        }
        return switch (risk == null ? BusinessRisk.UNKNOWN : risk) {
            case HIGH -> BusinessAcceptanceDecision.MANUAL_REVIEW;
            case MEDIUM, LOW -> targetDecision == BusinessAcceptanceDecision.UNKNOWN
                    ? BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING
                    : moreCautious(targetDecision, BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING);
            case NORMAL -> targetDecision == BusinessAcceptanceDecision.UNKNOWN
                    ? BusinessAcceptanceDecision.ACCEPTABLE
                    : targetDecision;
            case UNKNOWN -> targetDecision;
        };
    }

    private BusinessAcceptanceDecision moreCautious(BusinessAcceptanceDecision a, BusinessAcceptanceDecision b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private int rank(BusinessAcceptanceDecision decision) {
        if (decision == null) {
            return 0;
        }
        return switch (decision) {
            case UNKNOWN -> 0;
            case ACCEPTABLE -> 1;
            case ACCEPTABLE_WITH_WARNING -> 2;
            case MANUAL_REVIEW -> 3;
            case REJECT_SUGGESTED -> 4;
        };
    }

    private ReviewIssue strongerIssue(ReviewIssue a, ReviewIssue b) {
        int severityCompare = Integer.compare(severityRank(a.severity()), severityRank(b.severity()));
        if (severityCompare < 0) {
            return a;
        }
        if (severityCompare > 0) {
            return b;
        }
        double ac = a.confidence() == null ? -1.0 : a.confidence();
        double bc = b.confidence() == null ? -1.0 : b.confidence();
        if (Double.compare(bc, ac) > 0) {
            return b;
        }
        if (Boolean.TRUE.equals(b.verified()) && !Boolean.TRUE.equals(a.verified())) {
            return b;
        }
        return a;
    }

    private int severityRank(Severity s) {
        return switch (s) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case NORMAL -> 3;
            case UNKNOWN -> 4;
        };
    }

    private BusinessRisk riskFromTargetAssessment(TargetProductAssessment assessment) {
        if (assessment == null) {
            return BusinessRisk.UNKNOWN;
        }
        if (assessment.businessAcceptanceDecision() == BusinessAcceptanceDecision.REJECT_SUGGESTED
                || assessment.productIdentityDecision() == ProductIdentityDecision.PRODUCT_MISMATCH
                || assessment.decision() == TargetMatchDecision.MISMATCH) {
            return BusinessRisk.HIGH;
        }
        if (assessment.businessAcceptanceDecision() == BusinessAcceptanceDecision.MANUAL_REVIEW
                || assessment.decision() == TargetMatchDecision.INSUFFICIENT_EVIDENCE
                || assessment.decision() == TargetMatchDecision.UNKNOWN) {
            return BusinessRisk.MEDIUM;
        }
        if (assessment.businessAcceptanceDecision() == BusinessAcceptanceDecision.ACCEPTABLE_WITH_WARNING
                || assessment.decision() == TargetMatchDecision.POSSIBLE_MATCH
                || assessment.decision() == TargetMatchDecision.MATCH_BY_PRODUCT_FAMILY) {
            return BusinessRisk.LOW;
        }
        if (assessment.businessAcceptanceDecision() == BusinessAcceptanceDecision.ACCEPTABLE
                || assessment.decision() == TargetMatchDecision.MATCH
                || assessment.decision() == TargetMatchDecision.CONTAINED
                || assessment.decision() == TargetMatchDecision.ACCEPTABLE_BY_DISTRIBUTOR) {
            return BusinessRisk.NORMAL;
        }
        return BusinessRisk.UNKNOWN;
    }

    private boolean targetAssessmentAcceptsProduct(ReviewResult r) {
        TargetProductAssessment a = r == null ? null : r.targetProductAssessment();
        if (a == null) {
            return false;
        }
        return a.decision() == TargetMatchDecision.MATCH
                || a.decision() == TargetMatchDecision.CONTAINED
                || a.decision() == TargetMatchDecision.MATCH_BY_PRODUCT_FAMILY
                || a.decision() == TargetMatchDecision.ACCEPTABLE_BY_DISTRIBUTOR;
    }

    private boolean nameAllowedByMaster(String actualName, ReviewResult.ProductMasterInfo master) {
        if (!StringUtils.hasText(actualName) || master == null) {
            return false;
        }
        if (actualName.equals(master.productName())) {
            return true;
        }
        return master.aliases() != null && master.aliases().contains(actualName);
    }

    private ReviewIssue declaredTypeMismatchIssue(ReviewResult r) {
        if (r == null || r.declaredInfo() == null || r.candidateDocumentType() == null) {
            return null;
        }
        if (!StringUtils.hasText(r.declaredInfo().documentType())
                || !StringUtils.hasText(r.candidateDocumentType().value())
                || sameType(r.declaredInfo().documentType(), r.candidateDocumentType().value())
                || r.candidateDocumentType().confidence() == null
                || r.candidateDocumentType().confidence() < 0.8) {
            return null;
        }
        Integer page = null;
        String evidence = r.candidateDocumentType().reason();
        if (r.candidateDocumentType().evidence() != null && !r.candidateDocumentType().evidence().isEmpty()) {
            page = r.candidateDocumentType().evidence().get(0).pageNumber();
            evidence = r.candidateDocumentType().evidence().get(0).text();
        }
        return new ReviewIssue(
                IssueType.DECLARED_TYPE_MISMATCH,
                Severity.MEDIUM,
                r.candidateDocumentType().confidence(),
                page,
                evidence,
                "声明文件类型与正文候选文件类型不一致；系统保留两者，不用正文候选类型覆盖声明类型。",
                "建议人工确认文件类型声明来源和正文实际文件类型。",
                "MERGE",
                page == null || StringUtils.hasText(evidence));
    }

    private boolean sameType(String declared, String candidate) {
        if (!StringUtils.hasText(declared) || !StringUtils.hasText(candidate)) {
            return false;
        }
        DocumentType declaredType = documentTypeResolver.resolve(declared);
        DocumentType candidateType = documentTypeResolver.resolve(candidate);
        if (declaredType != DocumentType.UNKNOWN && candidateType != DocumentType.UNKNOWN) {
            return declaredType == candidateType;
        }
        return sameTypeText(declared, candidate);
    }

    private boolean sameTypeText(String declared, String candidate) {
        if (!StringUtils.hasText(declared) || !StringUtils.hasText(candidate)) {
            return false;
        }
        String d = declared.replaceAll("\\s+", "");
        String c = candidate.replaceAll("\\s+", "");
        return d.equals(c) || d.contains(c) || c.contains(d);
    }
}
