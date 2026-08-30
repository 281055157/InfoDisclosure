package com.example.disclosurereview.controller;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.dto.ReviewTaskDtos.CreateReviewResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.IssueUpdateRequest;
import com.example.disclosurereview.dto.ReviewTaskDtos.LlmCallResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.LlmUsageResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.ManualIssueRequest;
import com.example.disclosurereview.dto.ReviewTaskDtos.ManualReviewRequest;
import com.example.disclosurereview.dto.ReviewTaskDtos.PageResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.PageTextResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.RetryRequest;
import com.example.disclosurereview.dto.ReviewTaskDtos.ReviewReportResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.StatisticsSummaryResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.TaskSummaryResponse;
import com.example.disclosurereview.dto.ReviewTaskDtos.TimelineEntryResponse;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.ReviewResult;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TechnicalStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.service.ManualReviewService;
import com.example.disclosurereview.service.ReviewService;
import com.example.disclosurereview.service.ReviewTaskQueryService;
import com.example.disclosurereview.service.ReviewTaskService;
import com.example.disclosurereview.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@Validated
@Tag(name = "信息披露附件智能预审", description = "持久化异步审核、人工审核与工作台查询接口")
public class ReviewController {

    private static final long RANGE_CHUNK_SIZE = 1024 * 1024;

    private final ReviewTaskService reviewTaskService;
    private final ReviewTaskQueryService queryService;
    private final ManualReviewService manualReviewService;
    private final ReviewService reviewService;
    private final ReviewProperties reviewProperties;
    private final FileStorageService fileStorageService;
    private final ReviewTaskJpaRepository taskRepository;

    public ReviewController(ReviewTaskService reviewTaskService,
                            ReviewTaskQueryService queryService,
                            ManualReviewService manualReviewService,
                            ReviewService reviewService,
                            ReviewProperties reviewProperties,
                            FileStorageService fileStorageService,
                            ReviewTaskJpaRepository taskRepository) {
        this.reviewTaskService = reviewTaskService;
        this.queryService = queryService;
        this.manualReviewService = manualReviewService;
        this.reviewService = reviewService;
        this.reviewProperties = reviewProperties;
        this.fileStorageService = fileStorageService;
        this.taskRepository = taskRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建异步审核任务",
            description = "异步返回新任务编号；重复上传相同文件也会创建独立审核任务。")
    public ResponseEntity<CreateReviewResponse> createReview(
            @RequestPart("file") @NotNull(message = "PDF文件不能为空") MultipartFile file,
            @RequestPart(value = "parameterFile", required = false) MultipartFile parameterFile,
            @RequestParam(value = "documentCategory", required = false, defaultValue = "AUTO") DocumentCategory documentCategory,
            @RequestParam(value = "declaredProductCode", required = false) String declaredProductCode,
            @RequestParam(value = "declaredDocumentType", required = false) String declaredDocumentType,
            @RequestParam(value = "forceReview", required = false, defaultValue = "false") boolean forceReview,
            @RequestParam(value = "businessRecordId", required = false) String businessRecordId,
            @RequestParam(value = "mailId", required = false) String mailId,
            @RequestParam(value = "attachmentId", required = false) String attachmentId) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF文件不能为空");
        }
        CreateReviewResponse response = reviewTaskService.create(file, parameterFile, documentCategory,
                declaredProductCode, declaredDocumentType, forceReview, businessRecordId, mailId, attachmentId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "同步调试审核", description = "兼容原型阶段的同步接口，可通过 review.sync.enabled 关闭。")
    public ResponseEntity<ReviewResult> createReviewSync(
            @RequestPart("file") @NotNull(message = "PDF文件不能为空") MultipartFile file,
            @RequestPart(value = "parameterFile", required = false) MultipartFile parameterFile,
            @RequestParam(value = "documentCategory", required = false, defaultValue = "AUTO") DocumentCategory documentCategory,
            @RequestParam(value = "declaredProductCode", required = false) String declaredProductCode,
            @RequestParam(value = "declaredDocumentType", required = false) String declaredDocumentType) {
        if (!reviewProperties.getSync().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "同步调试接口已关闭");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF文件不能为空");
        }
        return ResponseEntity.ok(reviewService.review(file, parameterFile, documentCategory,
                declaredProductCode, declaredDocumentType));
    }

    @GetMapping
    @Operation(summary = "任务列表", description = "支持分页、关键字和状态筛选。")
    public PageResponse<TaskSummaryResponse> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) ReviewTaskStatus status,
            @RequestParam(value = "technicalStatus", required = false) TechnicalStatus technicalStatus,
            @RequestParam(value = "businessRisk", required = false) BusinessRisk businessRisk,
            @RequestParam(value = "documentCategory", required = false) DocumentCategory documentCategory,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "manualReviewStatus", required = false) String manualReviewStatus,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TaskSummaryResponse> page = queryService.list(keyword, status, technicalStatus, businessRisk,
                documentCategory, documentType, manualReviewStatus, createdFrom, createdTo, pageable);
        return PageResponse.from(page);
    }

    @GetMapping("/statistics/summary")
    public StatisticsSummaryResponse statistics() {
        return queryService.statistics();
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "任务详情", description = "数字 taskId 查询持久化任务；非数字 taskId 兼容旧同步内存结果。")
    public ResponseEntity<Object> getReview(@PathVariable String taskId) {
        Long id = parseLong(taskId);
        if (id != null) {
            return ResponseEntity.ok(queryService.detail(id));
        }
        return reviewService.findById(taskId)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "任务不存在: " + taskId));
    }

    @GetMapping("/{taskId}/report")
    public ReviewReportResponse report(@PathVariable Long taskId) {
        return queryService.report(taskId);
    }

    @GetMapping("/{taskId}/pages")
    public List<PageTextResponse> pages(@PathVariable Long taskId) {
        return queryService.pages(taskId);
    }

    @GetMapping("/{taskId}/pages/{pageNumber}")
    public PageTextResponse page(@PathVariable Long taskId, @PathVariable int pageNumber) {
        return queryService.page(taskId, pageNumber);
    }

    @GetMapping("/{taskId}/timeline")
    public List<TimelineEntryResponse> timeline(@PathVariable Long taskId) {
        return queryService.timeline(taskId);
    }

    @GetMapping("/{taskId}/llm-usage")
    public LlmUsageResponse llmUsage(@PathVariable Long taskId) {
        return queryService.llmUsage(taskId);
    }

    @GetMapping("/{taskId}/llm-calls")
    public List<LlmCallResponse> llmCalls(@PathVariable Long taskId) {
        return queryService.llmCalls(taskId);
    }

    @GetMapping("/{taskId}/file")
    public ResponseEntity<?> file(@PathVariable Long taskId,
                                  @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在: " + taskId));
        Resource resource = fileStorageService.load(task.getFilePath());
        long contentLength = resource.contentLength();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(inlineContentDisposition(task.getOriginalFileName()));
        if (rangeHeader == null || rangeHeader.isBlank()) {
            headers.setContentLength(contentLength);
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        }
        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        HttpRange range = ranges.isEmpty() ? HttpRange.createByteRange(0) : ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = Math.min(range.getRangeEnd(contentLength), start + RANGE_CHUNK_SIZE - 1);
        ResourceRegion region = new ResourceRegion(resource, start, end - start + 1);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(region);
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Void> retry(@PathVariable Long taskId,
                                      @RequestBody(required = false) RetryRequest request) {
        manualReviewService.retry(taskId, request == null ? null : request.stage());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{taskId}/manual-review")
    public ResponseEntity<Void> manualReview(@PathVariable Long taskId,
                                             @RequestBody ManualReviewRequest request) {
        manualReviewService.submitManualReview(taskId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{taskId}/issues/{issueId}")
    public ResponseEntity<Void> updateIssue(@PathVariable Long taskId,
                                            @PathVariable Long issueId,
                                            @RequestBody IssueUpdateRequest request) {
        try {
            manualReviewService.updateIssue(taskId, issueId, request);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskId}/issues/manual")
    public ResponseEntity<Void> addManualIssue(@PathVariable Long taskId,
                                               @RequestBody ManualIssueRequest request) {
        manualReviewService.addManualIssue(taskId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{taskId}/reopen")
    public ResponseEntity<Void> reopen(@PathVariable Long taskId) {
        manualReviewService.reopen(taskId);
        return ResponseEntity.ok().build();
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static ContentDisposition inlineContentDisposition(String fileName) {
        return ContentDisposition.inline()
                .filename(sanitizeHeaderFileName(fileName), StandardCharsets.UTF_8)
                .build();
    }

    static String sanitizeHeaderFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "review-file.pdf";
        }
        String normalized = fileName.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        normalized = normalized
                .replace("\r", "")
                .replace("\n", "")
                .replace("\"", "")
                .trim();
        return normalized.isBlank() ? "review-file.pdf" : normalized;
    }
}
