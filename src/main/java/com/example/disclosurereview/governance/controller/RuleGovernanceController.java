package com.example.disclosurereview.governance.controller;

import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.dto.RuleGovernanceDtos.*;
import com.example.disclosurereview.governance.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rule-governance")
public class RuleGovernanceController {
    private final RuleGovernanceQueryService queryService;
    private final FeedbackGovernanceRunService runService;
    private final RuleProposalReviewService reviewService;
    private final RuleCandidateValidationService validationService;
    private final RuleBacktestService backtestService;
    private final GovernanceEffectEvaluationService effectService;
    private final ObjectMapper mapper;
    private final GovernanceTraceQueryService traceQueryService;
    private final GovernanceRecordDeletionService deletionService;

    public RuleGovernanceController(RuleGovernanceQueryService queryService,
                                    FeedbackGovernanceRunService runService,
                                    RuleProposalReviewService reviewService,
                                    RuleCandidateValidationService validationService,
                                    RuleBacktestService backtestService,
                                    GovernanceEffectEvaluationService effectService,
                                    ObjectMapper mapper,
                                    GovernanceTraceQueryService traceQueryService,
                                    GovernanceRecordDeletionService deletionService) {
        this.queryService = queryService; this.runService = runService; this.reviewService = reviewService;
        this.validationService = validationService; this.backtestService = backtestService;
        this.effectService = effectService; this.mapper = mapper; this.traceQueryService = traceQueryService;
        this.deletionService = deletionService;
    }

    @GetMapping("/runs") public List<RunResponse> runs() { return queryService.runs(); }
    @GetMapping("/runs/{id}") public RunResponse run(@PathVariable Long id) { return queryService.run(id); }
    @GetMapping("/runs/{id}/trace") public TraceResponse trace(@PathVariable Long id) { return traceQueryService.trace(id); }
    @PostMapping("/runs") public ResponseEntity<RunResponse> start(@RequestHeader(value = "X-Operator", required = false) String operator) {
        var run = runService.start(GovernanceRunTriggerType.MANUAL, operator(operator));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queryService.run(run.getId()));
    }
    @DeleteMapping("/runs/{id}")
    public GovernanceRecordDeletionService.DeletionResult deleteRun(@PathVariable Long id,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return deletionService.deleteRun(id, operator(operator));
    }

    @GetMapping("/groups")
    public List<GroupResponse> groups(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String ruleCode,
                                      @RequestParam(required = false) String documentCategory) {
        return queryService.groups(status, ruleCode, documentCategory);
    }
    @GetMapping("/groups/{id}") public GroupResponse group(@PathVariable Long id) { return queryService.group(id); }
    @GetMapping("/groups/{id}/feedbacks") public List<FeedbackSampleResponse> feedbacks(@PathVariable Long id) { return queryService.feedbacks(id); }
    @PostMapping("/groups/{id}/analyze") public ResponseEntity<Void> analyze(@PathVariable Long id,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        runService.retryGroup(id, operator(operator)); return ResponseEntity.accepted().build();
    }
    @DeleteMapping("/groups/{id}")
    public GovernanceRecordDeletionService.DeletionResult deleteGroup(@PathVariable Long id,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return deletionService.deleteGroup(id, operator(operator));
    }

    @GetMapping("/proposals")
    public List<ProposalSummaryResponse> proposals(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String rootCause,
                                                   @RequestParam(required = false) String ruleCode) {
        return queryService.proposals(status, type, rootCause, ruleCode);
    }
    @GetMapping("/proposals/{id}") public ProposalDetailResponse proposal(@PathVariable Long id) { return queryService.proposal(id); }
    @GetMapping("/proposals/{id}/diff") public JsonNode diff(@PathVariable Long id) { return queryService.diff(id); }
    @GetMapping("/proposals/{id}/backtest") public JsonNode backtest(@PathVariable Long id) { return queryService.backtest(id); }

    @PostMapping("/proposals/{id}/approve")
    public ProposalDetailResponse approve(@PathVariable Long id, @RequestBody(required = false) ApproveRequest request,
                                          @RequestHeader(value = "X-Operator", required = false) String operator) {
        reviewService.approve(id, operator(operator), request == null ? null : request.comment()); return queryService.proposal(id);
    }
    @PostMapping("/proposals/{id}/approve-with-modification")
    public ProposalDetailResponse approveModified(@PathVariable Long id, @Valid @RequestBody ApproveWithModificationRequest request,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        reviewService.approveWithModification(id, request.candidateRule(), operator(operator), request.comment()); return queryService.proposal(id);
    }
    @PostMapping("/proposals/{id}/reject")
    public ProposalDetailResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                         @RequestHeader(value = "X-Operator", required = false) String operator) {
        reviewService.reject(id, request.reason(), operator(operator), request.comment()); return queryService.proposal(id);
    }
    @PostMapping("/proposals/{id}/defer")
    public ProposalDetailResponse defer(@PathVariable Long id, @Valid @RequestBody DeferRequest request,
                                        @RequestHeader(value = "X-Operator", required = false) String operator) {
        reviewService.defer(id, request.reason(), request.reviewAfter(), operator(operator)); return queryService.proposal(id);
    }
    @PostMapping("/proposals/{id}/apply")
    public ProposalDetailResponse apply(@PathVariable Long id, @RequestBody(required = false) ApplyRequest request,
                                        @RequestHeader(value = "X-Operator", required = false) String operator) {
        reviewService.applyDisable(id, operator(operator), request == null ? null : request.comment()); return queryService.proposal(id);
    }
    @PostMapping("/proposals/{id}/evaluate-effect")
    public GovernanceEffectEvaluationService.EffectResult evaluate(@PathVariable Long id,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return effectService.evaluate(id, operator(operator));
    }

    @GetMapping("/memories") public List<MemoryResponse> memories(@RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String documentCategory,
            @RequestParam(required = false) String declaredFileType,
            @RequestParam(required = false) String rootCauseType) {
        return queryService.memories(ruleCode, documentCategory, declaredFileType, rootCauseType);
    }
    @GetMapping("/memories/{id}") public MemoryResponse memory(@PathVariable Long id) { return queryService.memory(id); }

    @PostMapping("/validate-rule")
    public CandidateValidationResult validate(@Valid @RequestBody CandidateValidationRequest request) {
        return validationService.validate(RuleCandidate.from(request.candidateRule(), mapper),
                request.sourceRuleCode(), Boolean.TRUE.equals(request.creatingRule()));
    }
    @PostMapping("/backtest")
    public RuleBacktestResult backtest(@Valid @RequestBody BacktestRequest request) {
        return backtestService.run(request.feedbackGroupId(), RuleCandidate.from(request.candidateRule(), mapper),
                request.maximumSamples() == null ? 100 : request.maximumSamples());
    }

    private String operator(String value) { return StringUtils.hasText(value) ? value.strip() : "demo-user"; }
}
