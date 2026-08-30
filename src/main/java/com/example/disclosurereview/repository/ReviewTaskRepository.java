package com.example.disclosurereview.repository;

import com.example.disclosurereview.model.ReviewResult;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存任务仓：原型阶段任务结果保存在内存中，不接入数据库。
 */
@Repository
public class ReviewTaskRepository {

    private final ConcurrentHashMap<String, ReviewResult> store = new ConcurrentHashMap<>();

    public void save(ReviewResult result) {
        store.put(result.taskId(), result);
    }

    public Optional<ReviewResult> findById(String taskId) {
        return Optional.ofNullable(store.get(taskId));
    }

    public int size() {
        return store.size();
    }
}
