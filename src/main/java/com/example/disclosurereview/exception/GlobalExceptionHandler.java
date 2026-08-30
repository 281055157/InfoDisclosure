package com.example.disclosurereview.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import com.example.disclosurereview.storage.FileStorageException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        return build(e.getStatusCode().value(), e.getReason());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND.value(), e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, BindException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return build(HttpStatus.BAD_REQUEST.value(), "请求参数错误: " + e.getMessage());
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(FileStorageException e) {
        return build(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return build(HttpStatus.CONFLICT.value(), e.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticConflict(Exception e) {
        return build(HttpStatus.CONFLICT.value(), "数据已被其他用户修改，请刷新后重试");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSize(MaxUploadSizeExceededException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE.value(), "上传文件超出大小限制");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务内部错误: " + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("message", message == null ? "" : message);
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body);
    }
}
