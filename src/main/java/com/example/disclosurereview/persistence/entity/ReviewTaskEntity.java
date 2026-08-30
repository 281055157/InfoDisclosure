package com.example.disclosurereview.persistence.entity;

import com.example.disclosurereview.model.BusinessAcceptanceDecision;
import com.example.disclosurereview.model.BusinessRisk;
import com.example.disclosurereview.model.DocumentCategory;
import com.example.disclosurereview.model.ProductIdentityDecision;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.model.ReviewTaskStatus;
import com.example.disclosurereview.model.TechnicalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "review_task")
public class ReviewTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_no", nullable = false, unique = true, length = 64)
    private String taskNo;

    @Column(name = "business_record_id", length = 128)
    private String businessRecordId;

    @Column(name = "mail_id", length = 128)
    private String mailId;

    @Column(name = "attachment_id", length = 128)
    private String attachmentId;

    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 256)
    private String storedFileName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "parameter_file_path", length = 1024)
    private String parameterFilePath;

    @Column(name = "parameter_file_hash", length = 64)
    private String parameterFileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_category", nullable = false, length = 64)
    private DocumentCategory documentCategory;

    @Column(name = "declared_product_code", length = 128)
    private String declaredProductCode;

    @Column(name = "declared_document_type", length = 256)
    private String declaredDocumentType;

    @Column(name = "b9_value", length = 256)
    private String b9Value;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private ReviewTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "technical_status", length = 64)
    private TechnicalStatus technicalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_risk", length = 64)
    private BusinessRisk businessRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_identity_decision", length = 64)
    private ProductIdentityDecision productIdentityDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_acceptance_decision", length = 64)
    private BusinessAcceptanceDecision businessAcceptanceDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 64)
    private ReviewStage currentStage;

    @Column(name = "review_version", nullable = false, length = 64)
    private String reviewVersion;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 512)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "status_detail", columnDefinition = "text")
    private String statusDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "manual_reviewed_at")
    private Instant manualReviewedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getId() {
        return id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getBusinessRecordId() {
        return businessRecordId;
    }

    public void setBusinessRecordId(String businessRecordId) {
        this.businessRecordId = businessRecordId;
    }

    public String getMailId() {
        return mailId;
    }

    public void setMailId(String mailId) {
        this.mailId = mailId;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public void setStoredFileName(String storedFileName) {
        this.storedFileName = storedFileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getParameterFilePath() {
        return parameterFilePath;
    }

    public void setParameterFilePath(String parameterFilePath) {
        this.parameterFilePath = parameterFilePath;
    }

    public String getParameterFileHash() {
        return parameterFileHash;
    }

    public void setParameterFileHash(String parameterFileHash) {
        this.parameterFileHash = parameterFileHash;
    }

    public DocumentCategory getDocumentCategory() {
        return documentCategory;
    }

    public void setDocumentCategory(DocumentCategory documentCategory) {
        this.documentCategory = documentCategory;
    }

    public String getDeclaredProductCode() {
        return declaredProductCode;
    }

    public void setDeclaredProductCode(String declaredProductCode) {
        this.declaredProductCode = declaredProductCode;
    }

    public String getDeclaredDocumentType() {
        return declaredDocumentType;
    }

    public void setDeclaredDocumentType(String declaredDocumentType) {
        this.declaredDocumentType = declaredDocumentType;
    }

    public String getB9Value() {
        return b9Value;
    }

    public void setB9Value(String b9Value) {
        this.b9Value = b9Value;
    }

    public ReviewTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewTaskStatus status) {
        this.status = status;
    }

    public TechnicalStatus getTechnicalStatus() {
        return technicalStatus;
    }

    public void setTechnicalStatus(TechnicalStatus technicalStatus) {
        this.technicalStatus = technicalStatus;
    }

    public BusinessRisk getBusinessRisk() {
        return businessRisk;
    }

    public void setBusinessRisk(BusinessRisk businessRisk) {
        this.businessRisk = businessRisk;
    }

    public ProductIdentityDecision getProductIdentityDecision() {
        return productIdentityDecision;
    }

    public void setProductIdentityDecision(ProductIdentityDecision productIdentityDecision) {
        this.productIdentityDecision = productIdentityDecision;
    }

    public BusinessAcceptanceDecision getBusinessAcceptanceDecision() {
        return businessAcceptanceDecision;
    }

    public void setBusinessAcceptanceDecision(BusinessAcceptanceDecision businessAcceptanceDecision) {
        this.businessAcceptanceDecision = businessAcceptanceDecision;
    }

    public ReviewStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(ReviewStage currentStage) {
        this.currentStage = currentStage;
    }

    public String getReviewVersion() {
        return reviewVersion;
    }

    public void setReviewVersion(String reviewVersion) {
        this.reviewVersion = reviewVersion;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getManualReviewedAt() {
        return manualReviewedAt;
    }

    public void setManualReviewedAt(Instant manualReviewedAt) {
        this.manualReviewedAt = manualReviewedAt;
    }

    public Long getVersion() {
        return version;
    }
}
