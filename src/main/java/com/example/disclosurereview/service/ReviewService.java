package com.example.disclosurereview.service;

import com.example.disclosurereview.exception.ExcelParseException;
import com.example.disclosurereview.exception.LlmException;
import com.example.disclosurereview.exception.PdfEncryptedException;
import com.example.disclosurereview.exception.PdfParseException;
import com.example.disclosurereview.llm.LlmReviewService;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentScope;
import com.example.disclosurereview.model.DocumentType;
import com.example.disclosurereview.model.DocumentTypeAssessment;
import com.example.disclosurereview.model.EvidenceValue;
import com.example.disclosurereview.model.ExcelParameterResult;
import com.example.disclosurereview.model.FileNameInfo;
import com.example.disclosurereview.model.LlmReviewResult;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ProductOccurrence;
import com.example.disclosurereview.model.ProductReferenceRole;
import com.example.disclosurereview.model.ProductTableRow;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.TargetProductAssessment;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.parser.ExcelParameterParser;
import com.example.disclosurereview.parser.FileNameParser;
import com.example.disclosurereview.parser.PdfDocumentParser;
import com.example.disclosurereview.repository.ProductRepository;
import com.example.disclosurereview.repository.ReviewTaskRepository;
import com.example.disclosurereview.rule.PromptPolicyProvider;
import com.example.disclosurereview.rule.RuleEngine;
import com.example.disclosurereview.rule.RuleReviewService;
import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.strategy.DocumentReviewStrategy;
import com.example.disclosurereview.strategy.DocumentReviewStrategyRegistry;
import com.example.disclosurereview.strategy.DocumentTypeAliasResolver;
import com.example.disclosurereview.strategy.InstitutionRoleExtractor;
import com.example.disclosurereview.strategy.ProductCodeFamilyResolver;
import com.example.disclosurereview.strategy.ProductTableRowExtractor;
import com.example.disclosurereview.strategy.ReviewContext;
import com.example.disclosurereview.strategy.StrategyReviewPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审核主流程：解析 PDF/Excel/文件名 -> 规则审核 -> 产品库匹配 -> LLM 审核 -> 合并结果。
 * 所有失败路径都尽量降级，不让单个组件失败导致接口报错。
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final PdfDocumentParser pdfParser;
    private final ExcelParameterParser excelParser;
    private final FileNameParser fileNameParser;
    private final ProductRepository productRepository;
    private final RuleEngine ruleEngine;
    private final LlmReviewService llmReviewService;
    private final LlmProperties llmProperties;
    private final ReviewResultMerger merger;
    private final ReviewTaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final ReviewProperties reviewProperties;
    private final DocumentTypeAliasResolver documentTypeResolver;
    private final DocumentReviewStrategyRegistry strategyRegistry;
    private final ProductTableRowExtractor productTableRowExtractor;
    private final InstitutionRoleExtractor institutionRoleExtractor;
    private final PromptPolicyProvider promptPolicyProvider;
    private final DocumentCategoryResolver documentCategoryResolver;

    @Autowired
    public ReviewService(PdfDocumentParser pdfParser,
                         ExcelParameterParser excelParser,
                         FileNameParser fileNameParser,
                         ProductRepository productRepository,
                         RuleEngine ruleEngine,
                         LlmReviewService llmReviewService,
                         LlmProperties llmProperties,
                         ReviewResultMerger merger,
                         ReviewTaskRepository taskRepository,
                         ObjectMapper objectMapper,
                         ReviewProperties reviewProperties,
                         DocumentTypeAliasResolver documentTypeResolver,
                         DocumentReviewStrategyRegistry strategyRegistry,
                         ProductTableRowExtractor productTableRowExtractor,
                         InstitutionRoleExtractor institutionRoleExtractor,
                         PromptPolicyProvider promptPolicyProvider,
                         DocumentCategoryResolver documentCategoryResolver) {
        this.pdfParser = pdfParser;
        this.excelParser = excelParser;
        this.fileNameParser = fileNameParser;
        this.productRepository = productRepository;
        this.ruleEngine = ruleEngine;
        this.llmReviewService = llmReviewService;
        this.llmProperties = llmProperties;
        this.merger = merger;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.reviewProperties = reviewProperties;
        this.documentTypeResolver = documentTypeResolver;
        this.strategyRegistry = strategyRegistry;
        this.productTableRowExtractor = productTableRowExtractor;
        this.institutionRoleExtractor = institutionRoleExtractor;
        this.promptPolicyProvider = promptPolicyProvider == null ? PromptPolicyProvider.disabled() : promptPolicyProvider;
        this.documentCategoryResolver = documentCategoryResolver == null
                ? new DocumentCategoryResolver()
                : documentCategoryResolver;
    }

    public ReviewService(PdfDocumentParser pdfParser,
                         ExcelParameterParser excelParser,
                         FileNameParser fileNameParser,
                         ProductRepository productRepository,
                         RuleReviewService ruleReviewService,
                         LlmReviewService llmReviewService,
                         ReviewResultMerger merger,
                         ReviewTaskRepository taskRepository,
                         ObjectMapper objectMapper) {
        this(pdfParser, excelParser, fileNameParser, productRepository,
                new RuleEngine(ruleReviewService, null), llmReviewService,
                new LlmProperties(), merger, taskRepository, objectMapper, new ReviewProperties(),
                new DocumentTypeAliasResolver(new ReviewProperties()),
                DocumentReviewStrategyRegistry.defaultRegistry(),
                new ProductTableRowExtractor(new ProductCodeFamilyResolver()),
                new InstitutionRoleExtractor(),
                PromptPolicyProvider.disabled(),
                new DocumentCategoryResolver());
    }

    public ReviewService(PdfDocumentParser pdfParser,
                         ExcelParameterParser excelParser,
                         FileNameParser fileNameParser,
                         ProductRepository productRepository,
                         RuleReviewService ruleReviewService,
                         LlmReviewService llmReviewService,
                         LlmProperties llmProperties,
                         ReviewResultMerger merger,
                         ReviewTaskRepository taskRepository,
                         ObjectMapper objectMapper,
                         ReviewProperties reviewProperties,
                         DocumentTypeAliasResolver documentTypeResolver,
                         DocumentReviewStrategyRegistry strategyRegistry,
                         ProductTableRowExtractor productTableRowExtractor,
                         InstitutionRoleExtractor institutionRoleExtractor) {
        this(pdfParser, excelParser, fileNameParser, productRepository,
                new RuleEngine(ruleReviewService, null), llmReviewService, llmProperties, merger,
                taskRepository, objectMapper, reviewProperties, documentTypeResolver, strategyRegistry,
                productTableRowExtractor, institutionRoleExtractor, PromptPolicyProvider.disabled(),
                new DocumentCategoryResolver());
    }

    public ReviewResult review(MultipartFile file,
                               MultipartFile parameterFile,
                               DocumentCategory documentCategory,
                               String declaredProductCode,
                               String declaredDocumentType) {
        return review(file, parameterFile, documentCategory, declaredProductCode, declaredDocumentType, null);
    }

    public ReviewResult review(MultipartFile file,
                               MultipartFile parameterFile,
                               DocumentCategory documentCategory,
                               String declaredProductCode,
                               String declaredDocumentType,
                               Long persistentTaskId) {
        String taskId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        String fileName = file.getOriginalFilename();
        DocumentCategory requestedCategory = documentCategory == null ? DocumentCategory.AUTO : documentCategory;
        DocumentCategory category = requestedCategory;

        // 1. 解析 PDF（技术失败直接返回）
        List<DocumentPage> pages;
        try {
            try (InputStream in = file.getInputStream()) {
                pages = pdfParser.parse(in);
            }
        } catch (PdfEncryptedException e) {
            return saveAndReturn(technicalFailure(taskId, createdAt, fileName, category,
                    TechnicalStatus.PDF_ENCRYPTED, e.getMessage()));
        } catch (PdfParseException e) {
            return saveAndReturn(technicalFailure(taskId, createdAt, fileName, category,
                    TechnicalStatus.PDF_PARSE_FAILED, e.getMessage()));
        } catch (IOException e) {
            return saveAndReturn(technicalFailure(taskId, createdAt, fileName, category,
                    TechnicalStatus.PDF_PARSE_FAILED, "读取上传文件失败: " + e.getMessage()));
        }

        // 2. 解析文件名
        FileNameInfo fileNameInfo = fileNameParser.parse(fileName);

        // 3. 确定声明产品代码：外部传入优先，其次文件名
        String effectiveProductCode = StringUtils.hasText(declaredProductCode)
                ? declaredProductCode.strip()
                : fileNameInfo.productCode();

        // 4. 解析 Excel B9（可选，失败不阻断）
        String b9Value = null;
        boolean excelFailed = false;
        if (parameterFile != null && !parameterFile.isEmpty()) {
            try (InputStream in = parameterFile.getInputStream()) {
                ExcelParameterResult b9 = excelParser.parseB9(in);
                b9Value = b9.normalizedValue();
            } catch (ExcelParseException | IOException e) {
                log.warn("Excel解析失败，继续PDF审核: {}", e.getMessage());
                excelFailed = true;
            }
        }
        category = documentCategoryResolver.resolve(requestedCategory, b9Value);

        // 5. 确定声明文件类型：外部传入优先；协议类从文件名取；公告类从 B9 取
        String effectiveDocumentType = StringUtils.hasText(declaredDocumentType)
                ? declaredDocumentType.strip()
                : null;
        if (effectiveDocumentType == null) {
            if (category == DocumentCategory.ANNOUNCEMENT && StringUtils.hasText(b9Value)) {
                effectiveDocumentType = b9Value;
            } else {
                // 协议或无 B9 的 AUTO 场景默认从文件名提取
                effectiveDocumentType = fileNameInfo.declaredDocumentType();
            }
        }

        // 6. 产品库匹配
        Product matchedProduct = null;
        if (StringUtils.hasText(effectiveProductCode)) {
            Optional<Product> found = productRepository.findAny(effectiveProductCode);
            matchedProduct = found.orElse(null);
        }

        DocumentType declaredType = documentTypeResolver.resolve(effectiveDocumentType);
        DocumentType preLlmCandidateType = documentTypeResolver.detectFromPages(pages);
        DocumentType strategyCandidate = declaredType != DocumentType.UNKNOWN ? declaredType : preLlmCandidateType;

        // 7. 规则审核
        RuleReviewService.RuleReviewOutcome ruleOutcome = ruleEngine.review(
                pages, category, strategyCandidate, fileName, effectiveProductCode,
                effectiveDocumentType, b9Value, matchedProduct, persistentTaskId);

        DocumentReviewStrategy strategy = strategyRegistry.select(declaredType, preLlmCandidateType);
        ReviewContext preLlmContext = new ReviewContext(
                pages,
                fileName,
                category,
                effectiveProductCode,
                effectiveDocumentType,
                declaredType,
                preLlmCandidateType,
                b9Value,
                matchedProduct,
                reviewProperties.getInstitution().getTargetBankNames());
        StrategyReviewPolicy policy = strategy.buildPolicy(preLlmContext);
        List<ProductTableRow> ruleTargetRows = productTableRowExtractor.extractTargetRows(pages, effectiveProductCode, matchedProduct);
        List<ProductOccurrence> ruleOccurrences = buildProductOccurrences(ruleOutcome, effectiveProductCode, matchedProduct);

        // 8. LLM 审核（失败降级，不阻断）
        LlmReviewResult llmResult = null;
        boolean llmFailed = false;
        String llmFailureDetail = null;
        boolean llmSkipped = !llmProperties.isEnabled();
        if (llmSkipped) {
            llmFailed = true;
            llmFailureDetail = "LLM审核已关闭，仅使用规则结果";
        } else {
            try {
                llmResult = llmReviewService.review(
                        pages,
                        fileName,
                        category.name(),
                        effectiveProductCode,
                        effectiveDocumentType,
                        b9Value,
                        toJson(productMasterJson(matchedProduct, effectiveProductCode)),
                        toJson(productFamilyJson(matchedProduct, effectiveProductCode)),
                        toJson(reviewProperties.getInstitution().getTargetBankNames()),
                        preLlmCandidateType == DocumentType.UNKNOWN ? "" : preLlmCandidateType.displayName(),
                        mergePromptPolicy(policy.promptPolicy(),
                                promptPolicyProvider.additionalPolicy(category, strategyCandidate,
                                        effectiveProductCode, effectiveDocumentType, matchedProduct)),
                        toJson(ruleOutcome.productCodeCandidates()),
                        toJson(ruleOutcome.productNameCandidates()),
                        toJson(ruleTargetRows));
            } catch (LlmException e) {
                llmFailed = true;
                llmFailureDetail = e.getMessage();
                log.info("LLM审核失败，保留规则结果: {}", e.getMessage());
            } catch (Exception e) {
                llmFailed = true;
                llmFailureDetail = e.getMessage();
                log.info("LLM审核异常，保留规则结果: {}", e.getMessage());
            }
        }

        DocumentType llmCandidateType = llmResult != null && llmResult.candidateDocumentType() != null
                ? documentTypeResolver.resolve(llmResult.candidateDocumentType().value())
                : DocumentType.UNKNOWN;
        DocumentType finalCandidateType = llmCandidateType != DocumentType.UNKNOWN ? llmCandidateType : preLlmCandidateType;
        DocumentReviewStrategy finalStrategy = strategyRegistry.select(declaredType, finalCandidateType);
        ReviewContext finalContext = new ReviewContext(
                pages,
                fileName,
                category,
                effectiveProductCode,
                effectiveDocumentType,
                declaredType,
                finalCandidateType,
                b9Value,
                matchedProduct,
                reviewProperties.getInstitution().getTargetBankNames());
        TargetProductAssessment targetAssessment = finalStrategy.evaluate(finalContext, ruleOutcome, llmResult);
        DocumentScope documentScope = targetAssessment == null ? null : targetAssessment.documentScope();
        if (documentScope == null && llmResult != null) {
            documentScope = llmResult.documentScope();
        }
        DocumentTypeAssessment candidateAssessment = candidateTypeAssessment(finalCandidateType, llmResult, preLlmCandidateType);
        List<ProductTableRow> targetRows = mergeLists(ruleTargetRows,
                llmResult == null ? List.of() : llmResult.targetProductRows());
        List<ProductOccurrence> productOccurrences = mergeLists(ruleOccurrences,
                llmResult == null ? List.of() : llmResult.productOccurrences());
        var agencyAssessment = bestAgencyAssessment(
                institutionRoleExtractor.assess(pages, reviewProperties.getInstitution().getTargetBankNames(),
                        finalCandidateType == DocumentType.DISTRIBUTION_AGREEMENT || declaredType == DocumentType.DISTRIBUTION_AGREEMENT),
                llmResult == null ? null : llmResult.agencyAssessment());

        // 9. 技术状态
        TechnicalStatus status;
        String statusDetail = null;
        if (llmFailed) {
            status = TechnicalStatus.LLM_FAILED;
            statusDetail = llmSkipped ? llmFailureDetail : "LLM调用失败: " + llmFailureDetail;
        } else if (excelFailed) {
            status = TechnicalStatus.PARTIAL_SUCCESS;
            statusDetail = "Excel解析失败，PDF审核已完成";
        } else {
            status = TechnicalStatus.SUCCESS;
        }

        // 10. 组装结果
        ReviewResult withoutRisk = new ReviewResult(
                taskId,
                status,
                null,
                new ReviewResult.FileInfo(fileName, category, pages.size()),
                new ReviewResult.DeclaredInfo(effectiveProductCode, effectiveDocumentType, b9Value),
                toProductMasterInfo(matchedProduct),
                new ReviewResult.RuleResultInfo(
                        ruleOutcome.productCodeCandidates(),
                        ruleOutcome.productNameCandidates(),
                        ruleOutcome.placeholders(),
                        ruleOutcome.issues()),
                llmResult == null ? ReviewResult.LlmResultInfo.empty() : ReviewResult.LlmResultInfo.from(llmResult),
                documentScope,
                candidateAssessment,
                targetAssessment,
                targetRows,
                productOccurrences,
                agencyAssessment,
                List.of(),
                statusDetail,
                createdAt,
                Instant.now());

        List<ReviewIssue> mergedIssues = merger.mergeIssues(withoutRisk);
        BusinessRisk risk = merger.mergeRisk(withoutRisk);

        ReviewResult result = new ReviewResult(
                withoutRisk.taskId(),
                withoutRisk.technicalStatus(),
                risk,
                withoutRisk.fileInfo(),
                withoutRisk.declaredInfo(),
                withoutRisk.productMaster(),
                withoutRisk.ruleResult(),
                withoutRisk.llmResult(),
                withoutRisk.documentScope(),
                withoutRisk.candidateDocumentType(),
                withoutRisk.targetProductAssessment(),
                withoutRisk.targetProductRows(),
                withoutRisk.productOccurrences(),
                withoutRisk.agencyAssessment(),
                mergedIssues,
                withoutRisk.statusDetail(),
                withoutRisk.createdAt(),
                withoutRisk.completedAt());

        return saveAndReturn(result);
    }

    public Optional<ReviewResult> findById(String taskId) {
        return taskRepository.findById(taskId);
    }

    private ReviewResult saveAndReturn(ReviewResult result) {
        taskRepository.save(result);
        return result;
    }

    private ReviewResult technicalFailure(String taskId, Instant createdAt, String fileName,
                                          DocumentCategory category, TechnicalStatus status, String detail) {
        return new ReviewResult(
                taskId,
                status,
                BusinessRisk.UNKNOWN,
                new ReviewResult.FileInfo(fileName, category, 0),
                new ReviewResult.DeclaredInfo(null, null, null),
                ReviewResult.ProductMasterInfo.notMatched(),
                ReviewResult.RuleResultInfo.empty(),
                ReviewResult.LlmResultInfo.empty(),
                List.of(),
                detail,
                createdAt,
                Instant.now());
    }

    private ReviewResult.ProductMasterInfo toProductMasterInfo(Product product) {
        if (product == null) {
            return ReviewResult.ProductMasterInfo.notMatched();
        }
        return new ReviewResult.ProductMasterInfo(true, product.productCode(),
                product.productName(),
                product.safeAliases(),
                product.managerName(),
                product.issuerName(),
                product.parentProductCode(),
                product.safeShareCodes(),
                product.safeCodeAliases(),
                product.safeSeriesNames(),
                product.safeDistributorNames(),
                product.productType());
    }

    private Object productMasterJson(Product product, String declaredProductCode) {
        if (product == null) {
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("matched", false);
            data.put("lookupStatus", "NOT_FOUND");
            data.put("declaredProductCode", declaredProductCode == null ? "" : declaredProductCode);
            data.put("knownProductCodes", productRepository.allProductCodes());
            data.put("lookupMessage", "声明产品代码未在当前产品库中找到，不得自动匹配到其他已知产品");
            return data;
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("matched", true);
        data.put("lookupStatus", "MATCHED");
        data.put("declaredProductCode", declaredProductCode == null ? "" : declaredProductCode);
        data.put("productCode", product.productCode() == null ? "" : product.productCode());
        data.put("productName", product.productName() == null ? "" : product.productName());
        data.put("aliases", product.safeAliases());
        data.put("managerName", product.managerName() == null ? "" : product.managerName());
        data.put("issuerName", product.issuerName() == null ? "" : product.issuerName());
        data.put("parentProductCode", product.parentProductCode() == null ? "" : product.parentProductCode());
        data.put("shareCodes", product.safeShareCodes());
        data.put("codeAliases", product.safeCodeAliases());
        data.put("seriesNames", product.safeSeriesNames());
        data.put("distributorNames", product.safeDistributorNames());
        data.put("productType", product.productType() == null ? "" : product.productType());
        return data;
    }

    private Object productFamilyJson(Product product, String declaredProductCode) {
        if (product == null) {
            return java.util.Map.of(
                    "matched", false,
                    "lookupStatus", "NOT_FOUND",
                    "declaredProductCode", declaredProductCode == null ? "" : declaredProductCode,
                    "knownProductCodes", productRepository.allProductCodes());
        }
        return java.util.Map.of(
                "matched", true,
                "lookupStatus", "MATCHED",
                "declaredProductCode", declaredProductCode == null ? "" : declaredProductCode,
                "parentProductCode", product.parentProductCode() == null ? "" : product.parentProductCode(),
                "shareCodes", product.safeShareCodes(),
                "codeAliases", product.safeCodeAliases(),
                "seriesNames", product.safeSeriesNames());
    }

    private List<ProductOccurrence> buildProductOccurrences(RuleReviewService.RuleReviewOutcome ruleOutcome,
                                                            String declaredProductCode,
                                                            Product targetProduct) {
        if (ruleOutcome == null || ruleOutcome.productCodeCandidates() == null) {
            return List.of();
        }
        ProductCodeFamilyResolver familyResolver = new ProductCodeFamilyResolver();
        return ruleOutcome.productCodeCandidates().stream()
                .map(c -> new ProductOccurrence(
                        c.value(),
                        targetProduct != null && familyResolver.isExactTargetCode(c.value(), declaredProductCode, targetProduct)
                                ? targetProduct.productName()
                                : null,
                        familyResolver.isExactTargetCode(c.value(), declaredProductCode, targetProduct)
                                ? ProductReferenceRole.TARGET_PRODUCT
                                : ProductReferenceRole.CO_DISCLOSED_PRODUCT,
                        c.pageNumber(),
                        c.evidenceText(),
                        familyResolver.isExactTargetCode(c.value(), declaredProductCode, targetProduct) ? 0.9 : 0.55))
                .distinct()
                .toList();
    }

    private DocumentTypeAssessment candidateTypeAssessment(DocumentType finalCandidateType,
                                                           LlmReviewResult llmResult,
                                                           DocumentType preLlmCandidateType) {
        if (llmResult != null && llmResult.candidateDocumentType() != null) {
            return llmResult.candidateDocumentType();
        }
        DocumentType type = finalCandidateType != DocumentType.UNKNOWN ? finalCandidateType : preLlmCandidateType;
        if (type == null || type == DocumentType.UNKNOWN) {
            return null;
        }
        return new DocumentTypeAssessment(type.displayName(), 0.55,
                "规则根据文件名、B9或正文关键词得到的候选文件类型。", List.of());
    }

    private <T> List<T> mergeLists(List<T> first, List<T> second) {
        List<T> merged = new java.util.ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return merged.stream().distinct().toList();
    }

    private com.example.disclosurereview.model.AgencyAssessment bestAgencyAssessment(
            com.example.disclosurereview.model.AgencyAssessment ruleAssessment,
            com.example.disclosurereview.model.AgencyAssessment llmAssessment) {
        if (llmAssessment == null) {
            return ruleAssessment;
        }
        if (ruleAssessment == null) {
            return llmAssessment;
        }
        double ruleConfidence = ruleAssessment.confidence() == null ? 0.0 : ruleAssessment.confidence();
        double llmConfidence = llmAssessment.confidence() == null ? 0.0 : llmAssessment.confidence();
        return llmConfidence > ruleConfidence ? llmAssessment : ruleAssessment;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String mergePromptPolicy(String base, String dynamicPolicy) {
        if (!StringUtils.hasText(dynamicPolicy)) {
            return base;
        }
        if (!StringUtils.hasText(base)) {
            return dynamicPolicy;
        }
        return base + "\n" + dynamicPolicy;
    }
}
