package com.example.disclosurereview.service;

import com.example.disclosurereview.dto.AdminConfigDtos.DeleteTaskResponse;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.persistence.repository.ReviewTaskJpaRepository;
import com.example.disclosurereview.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminTaskManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminTaskManagementService.class);

    private final ReviewTaskJpaRepository taskRepository;
    private final FileStorageService fileStorageService;

    public AdminTaskManagementService(ReviewTaskJpaRepository taskRepository,
                                      FileStorageService fileStorageService) {
        this.taskRepository = taskRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public DeleteTaskResponse deleteTask(Long taskId, boolean deleteFiles) {
        ReviewTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        String taskNo = task.getTaskNo();
        String originalFileName = task.getOriginalFileName();
        String filePath = task.getFilePath();
        String parameterFilePath = task.getParameterFilePath();

        taskRepository.delete(task);
        taskRepository.flush();

        List<String> warnings = new ArrayList<>();
        boolean fileDeleted = false;
        boolean parameterFileDeleted = false;
        if (deleteFiles) {
            fileDeleted = deleteStorageFile(filePath, warnings, "file");
            parameterFileDeleted = !StringUtils.hasText(parameterFilePath)
                    || deleteStorageFile(parameterFilePath, warnings, "parameterFile");
        }
        return new DeleteTaskResponse(taskId, taskNo, originalFileName,
                true, fileDeleted, parameterFileDeleted, warnings);
    }

    private boolean deleteStorageFile(String storageKey, List<String> warnings, String label) {
        if (!StringUtils.hasText(storageKey)) {
            return true;
        }
        try {
            if (!fileStorageService.exists(storageKey)) {
                warnings.add(label + " already missing: " + storageKey);
                return false;
            }
            fileStorageService.delete(storageKey);
            return true;
        } catch (Exception e) {
            warnings.add(label + " delete failed: " + e.getMessage());
            log.warn("Failed to delete {} for storageKey={}: {}", label, storageKey, e.getMessage());
            return false;
        }
    }
}
