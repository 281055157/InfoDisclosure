package com.example.disclosurereview.dto;

import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DecisionEvidence;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.DocumentTypeAssessment;
import com.example.disclosurereview.model.ManualReviewDecision;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ReviewIssue;
import com.example.disclosurereview.model.ReviewIssueStatus;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TechnicalStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

public final class ReviewTaskDtos {

    private ReviewTaskDtos() {
    }

    public record CreateReviewResponse(
            Long taskId,
            String taskNo,
            ReviewTaskStatus status,
            boolean duplicate
    ) {
    }

    public record TaskSummaryResponse(
            Long taskId,
            String taskNo,
            String originalFileName,
            DocumentCategory documentCategory,
            String declaredProductCode,
            String declaredDocumentType,
            ReviewTaskStatus status,
            TechnicalStatus technicalStatus,
            BusinessRisk businessRisk,
            ProductIdentityDecision productIdentityDecision,
            BusinessAcceptanceDecision businessAcceptanceDecision,
            String currentStage,
            int retryCount,
            long llmInputTokens,
            long llmOutputTokens,
            long llmCacheHitTokens,
            Instant createdAt,
            Instant completedAt,
            Instant manualReviewedAt
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            boolean empty
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast(),
                    page.isEmpty());
        }
    }

    public record TaskDetailResponse(
            Long taskId,
            String taskNo,
            String originalFileName,
            DocumentCategory documentCategory,
            String declaredProductCode,
            String declaredDocumentType,
            String b9Value,
            ReviewTaskStatus status,
            TechnicalStatus technicalStatus,
            BusinessRisk businessRisk,
            ProductIdentityDecision productIdentityDecision,
            BusinessAcceptanceDecision businessAcceptanceDecision,
            String currentStage,
            String statusDetail,
            String reviewVersion,
            int retryCount,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant manualReviewedAt,
            long pageCount,
            long openIssueCount,
            ReviewResult reviewResult
    ) {
    }

    public record ReviewReportResponse(
            Long taskId,
            String taskNo,
            ReviewResult reviewResult,
            List<IssueResponse> issues,
            List<DecisionEvidence> evidenceChain,
            List<ManualReviewResponse> manualReviews,
            String summary,
            String manualSuggestion
    ) {
    }

    public record IssueResponse(
            Long issueId,
            String issueCode,
            String issueName,
            String severity,
            Double confidence,
            Integer pageNumber,
            String evidenceText,
            boolean evidenceVerified,
            String explanation,
            String suggestion,
            String sourceType,
            String ruleCode,
            Long ruleVersionId,
            Long ruleExecutionId,
            ReviewIssueStatus issueStatus,
            String falsePositiveStatus,
            IssueFeedbackResponse falsePositiveFeedback,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record IssueFeedbackResponse(
            Long feedbackId,
            String feedbackType,
            String processStatus,
            String comment,
            String reviewer,
            String manualSnapshotJson,
            Instant createdAt,
            Instant processedAt
    ) {
    }

    public record PageTextResponse(
            Long taskId,
            int pageNumber,
            String rawText,
            String normalizedText,
            int charCount
    ) {
    }

    public record TimelineEntryResponse(
            Long id,
            String operationType,
            String operator,
            String operationDetail,
            String beforeValue,
            String afterValue,
            String traceId,
            Instant createdAt
    ) {
    }

    public record LlmUsageResponse(
            Long taskId,
            long inputTokens,
            long outputTokens,
            long cacheHitTokens,
            long callCount
    ) {
    }

    public record LlmCallResponse(
            Long id,
            String stage,
            String operationType,
            String provider,
            String modelName,
            String ruleCode,
            Long ruleVersionId,
            Integer chunkIndex,
            Integer pageFrom,
            Integer pageTo,
            Integer inputTokens,
            Integer outputTokens,
            Integer cacheHitTokens,
            Long durationMs,
            String callStatus,
            String errorMessage,
            Instant createdAt
    ) {
    }

    public record StatisticsSummaryResponse(
            long total,
            long waitingManualReview,
            long highRisk,
            long partialSuccess,
            long completedToday,
            double averageProcessingSeconds
    ) {
    }

    public record RetryRequest(ReviewStage stage) {
    }

    public record ManualReviewRequest(
            ManualReviewDecision decision,
            String comment,
            Boolean aiResultCorrect,
            Boolean containsFalsePositive,
            Boolean containsFalseNegative,
            List<String> actualIssueTypes,
            String reviewer
    ) {
    }

    public record IssueUpdateRequest(
            ReviewIssueStatus issueStatus,
            String comment
    ) {
    }

    public record ManualIssueRequest(
            ReviewIssue issue,
            String comment,
            String reviewer
    ) {
    }

    public record ManualReviewResponse(
            Long id,
            ManualReviewDecision decision,
            String comment,
            Boolean aiResultCorrect,
            Boolean containsFalsePositive,
            Boolean containsFalseNegative,
            List<String> actualIssueTypes,
            String reviewer,
            Instant reviewedAt
    ) {
    }
}
