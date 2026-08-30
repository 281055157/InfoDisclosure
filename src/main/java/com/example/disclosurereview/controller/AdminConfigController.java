package com.example.disclosurereview.controller;

import com.example.disclosurereview.dto.AdminConfigDtos.ModelConfigRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.ModelConfigResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.ModelTestResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.ProviderConfigRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.ProviderConfigResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.DeleteTaskResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.ExecutorSchemaResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleConfigRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleConfigResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleDetailResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleExecutionResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleFeedbackResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleTestRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleTestResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleValidationResponse;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleVersionRequest;
import com.example.disclosurereview.dto.AdminConfigDtos.RuleVersionResponse;
import com.example.disclosurereview.service.AdminConfigService;
import com.example.disclosurereview.service.AdminTaskManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminConfigController {

    private final AdminConfigService service;
    private final AdminTaskManagementService taskManagementService;

    public AdminConfigController(AdminConfigService service,
                                 AdminTaskManagementService taskManagementService) {
        this.service = service;
        this.taskManagementService = taskManagementService;
    }

    @GetMapping("/rules")
    public List<RuleConfigResponse> rules() {
        return service.rules();
    }

    @PostMapping("/rules")
    public ResponseEntity<RuleConfigResponse> createRule(@RequestBody RuleConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRule(request));
    }

    @GetMapping("/rules/executor-schemas")
    public ExecutorSchemaResponse executorSchemas() {
        return service.executorSchemas();
    }

    @GetMapping("/rules/{id}")
    public RuleDetailResponse rule(@PathVariable Long id) {
        return service.rule(id);
    }

    @PutMapping("/rules/{id}")
    public RuleConfigResponse updateRule(@PathVariable Long id, @RequestBody RuleConfigRequest request) {
        return service.updateRule(id, request);
    }

    @PostMapping("/rules/{id}/versions")
    public ResponseEntity<RuleVersionResponse> createRuleVersion(@PathVariable Long id,
                                                                 @RequestBody RuleVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRuleVersion(id, request));
    }

    @PutMapping("/rules/{id}/versions/{versionId}")
    public RuleVersionResponse updateRuleVersion(@PathVariable Long id,
                                                 @PathVariable Long versionId,
                                                 @RequestBody RuleVersionRequest request) {
        return service.updateRuleVersion(id, versionId, request);
    }

    @PostMapping("/rules/{id}/versions/{versionId}/validate")
    public RuleValidationResponse validateRuleVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return service.validateRuleVersion(id, versionId);
    }

    @PostMapping("/rules/{id}/versions/{versionId}/test")
    public RuleTestResponse testRuleVersion(@PathVariable Long id,
                                            @PathVariable Long versionId,
                                            @RequestBody RuleTestRequest request) {
        return service.testRuleVersion(id, versionId, request);
    }

    @PostMapping("/rules/{id}/versions/{versionId}/publish")
    public RuleVersionResponse publishRuleVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return service.publishRuleVersion(id, versionId);
    }

    @PostMapping("/rules/{id}/enable")
    public RuleConfigResponse enableRule(@PathVariable Long id) {
        return service.setRuleEnabled(id, true);
    }

    @PostMapping("/rules/{id}/disable")
    public RuleConfigResponse disableRule(@PathVariable Long id) {
        return service.setRuleEnabled(id, false);
    }

    @GetMapping("/rules/feedback")
    public List<RuleFeedbackResponse> ruleFeedback(@RequestParam(value = "taskId", required = false) Long taskId) {
        return service.feedback(taskId);
    }

    @GetMapping("/rules/{id}/executions")
    public List<RuleExecutionResponse> ruleExecutions(@PathVariable Long id) {
        return service.ruleExecutions(id);
    }

    @GetMapping("/models")
    public List<ModelConfigResponse> models() {
        return service.models();
    }

    @GetMapping("/providers")
    public List<ProviderConfigResponse> providers() {
        return service.providers();
    }

    @PostMapping("/providers")
    public ResponseEntity<ProviderConfigResponse> createProvider(@RequestBody ProviderConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProvider(request));
    }

    @PutMapping("/providers/{id}")
    public ProviderConfigResponse updateProvider(@PathVariable Long id, @RequestBody ProviderConfigRequest request) {
        return service.updateProvider(id, request);
    }

    @PostMapping("/providers/{id}/enable")
    public ProviderConfigResponse enableProvider(@PathVariable Long id) {
        return service.setProviderEnabled(id, true);
    }

    @PostMapping("/providers/{id}/disable")
    public ProviderConfigResponse disableProvider(@PathVariable Long id) {
        return service.setProviderEnabled(id, false);
    }

    @PostMapping("/models")
    public ResponseEntity<ModelConfigResponse> createModel(@RequestBody ModelConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createModel(request));
    }

    @PutMapping("/models/{id}")
    public ModelConfigResponse updateModel(@PathVariable Long id, @RequestBody ModelConfigRequest request) {
        return service.updateModel(id, request);
    }

    @PostMapping("/models/{id}/test")
    public ModelTestResponse testModel(@PathVariable Long id) {
        return service.testModel(id);
    }

    @DeleteMapping("/tasks/{taskId}")
    public DeleteTaskResponse deleteTask(@PathVariable Long taskId,
                                         @RequestParam(value = "deleteFiles", defaultValue = "true") boolean deleteFiles) {
        return taskManagementService.deleteTask(taskId, deleteFiles);
    }
}
