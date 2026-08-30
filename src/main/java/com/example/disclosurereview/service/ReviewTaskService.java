package com.example.disclosurereview.service;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.dto.ReviewTaskDtos.CreateReviewResponse;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.storage.FileStorageService;
import com.example.disclosurereview.storage.StoredFile;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReviewTaskService {

    private final FileStorageService fileStorageService;
    private final ReviewTaskJpaRepository taskRepository;
    private final ReviewTaskDispatcher dispatcher;
    private final ReviewProperties properties;
    private final TaskNoGenerator taskNoGenerator;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;

    public ReviewTaskService(FileStorageService fileStorageService,
                             ReviewTaskJpaRepository taskRepository,
                             ReviewTaskDispatcher dispatcher,
                             ReviewProperties properties,
                             TaskNoGenerator taskNoGenerator,
                             AuditLogService auditLogService,
                             MeterRegistry meterRegistry) {
        this.fileStorageService = fileStorageService;
        this.taskRepository = taskRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.taskNoGenerator = taskNoGenerator;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CreateReviewResponse create(MultipartFile file,
                                       MultipartFile parameterFile,
                                       DocumentCategory documentCategory,
                                       String declaredProductCode,
                                       String declaredDocumentType,
                                       boolean forceReview,
                                       String businessRecordId,
                                       String mailId,
                                       String attachmentId) {
        StoredFile storedFile = save(file);
        StoredFile storedParameterFile = parameterFile == null || parameterFile.isEmpty()
                ? null
                : save(parameterFile);
        String reviewVersion = StringUtils.hasText(properties.getReviewVersion())
                ? properties.getReviewVersion().strip()
                : "v1";
        String baseIdempotencyKey = storedFile.sha256() + ":"
                + (storedParameterFile == null ? "" : storedParameterFile.sha256()) + ":"
                + reviewVersion;
        // 同一附件可能需要在规则发布后重新审核。文件哈希继续用于追踪，但不再作为任务去重条件。
        // forceReview 参数仅为兼容旧客户端保留；所有上传现在都具有相同的“新建任务”语义。
        String submissionKey = baseIdempotencyKey + ":submission:" + UUID.randomUUID();
        ReviewTaskEntity created = createEntity(storedFile, storedParameterFile, documentCategory, declaredProductCode,
                declaredDocumentType, reviewVersion, submissionKey, businessRecordId, mailId, attachmentId);

        dispatcher.process(created.getId());
        meterRegistry.counter("review_task_created_total").increment();
        return new CreateReviewResponse(created.getId(), created.getTaskNo(), ReviewTaskStatus.CREATED, false);
    }

    protected ReviewTaskEntity createEntity(StoredFile storedFile,
                                            StoredFile parameterFile,
                                            DocumentCategory documentCategory,
                                            String declaredProductCode,
                                            String declaredDocumentType,
                                            String reviewVersion,
                                            String idempotencyKey,
                                            String businessRecordId,
                                            String mailId,
                                            String attachmentId) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTaskNo(taskNoGenerator.next());
        task.setBusinessRecordId(blankToNull(businessRecordId));
        task.setMailId(blankToNull(mailId));
        task.setAttachmentId(blankToNull(attachmentId));
        task.setOriginalFileName(storedFile.originalFileName());
        task.setStoredFileName(storedFile.storedFileName());
        task.setFilePath(storedFile.storageKey());
        task.setFileHash(storedFile.sha256());
        if (parameterFile != null) {
            task.setParameterFilePath(parameterFile.storageKey());
            task.setParameterFileHash(parameterFile.sha256());
        }
        task.setDocumentCategory(documentCategory == null ? DocumentCategory.AUTO : documentCategory);
        task.setDeclaredProductCode(blankToNull(declaredProductCode));
        task.setDeclaredDocumentType(blankToNull(declaredDocumentType));
        task.setStatus(ReviewTaskStatus.CREATED);
        task.setReviewVersion(reviewVersion);
        task.setIdempotencyKey(idempotencyKey);
        task.setRetryCount(0);
        task.setCreatedAt(Instant.now());
        ReviewTaskEntity saved = taskRepository.saveAndFlush(task);
        auditLogService.record(saved, "TASK_CREATED", "SYSTEM", "审核任务已创建",
                null, saved.getStatus().name());
        return saved;
    }

    private StoredFile save(MultipartFile file) {
        try {
            return fileStorageService.save(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalArgumentException("读取上传文件失败: " + e.getMessage(), e);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
