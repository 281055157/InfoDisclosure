package com.example.disclosurereview.persistence.entity;

import com.example.disclosurereview.model.ExtractSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "extracted_field")
public class ExtractedFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ReviewTaskEntity task;

    @Column(name = "field_type", nullable = false, length = 128)
    private String fieldType;

    @Column(name = "field_value", length = 1024)
    private String fieldValue;

    @Column(name = "normalized_value", length = 1024)
    private String normalizedValue;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "sheet_name", length = 256)
    private String sheetName;

    @Column(name = "cell_address", length = 64)
    private String cellAddress;

    @Column(name = "evidence_text", columnDefinition = "text")
    private String evidenceText;

    @Enumerated(EnumType.STRING)
    @Column(name = "extract_source", nullable = false, length = 64)
    private ExtractSource extractSource;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public ReviewTaskEntity getTask() {
        return task;
    }

    public void setTask(ReviewTaskEntity task) {
        this.task = task;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    public void setNormalizedValue(String normalizedValue) {
        this.normalizedValue = normalizedValue;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getCellAddress() {
        return cellAddress;
    }

    public void setCellAddress(String cellAddress) {
        this.cellAddress = cellAddress;
    }

    public String getEvidenceText() {
        return evidenceText;
    }

    public void setEvidenceText(String evidenceText) {
        this.evidenceText = evidenceText;
    }

    public ExtractSource getExtractSource() {
        return extractSource;
    }

    public void setExtractSource(ExtractSource extractSource) {
        this.extractSource = extractSource;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
