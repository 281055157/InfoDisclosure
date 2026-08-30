package com.example.disclosurereview.rule.executor;

import com.example.disclosurereview.rule.domain.RuleExecutorType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RuleExecutorRegistry {

    private final Map<RuleExecutorType, RuleExecutor> executors = new EnumMap<>(RuleExecutorType.class);

    public RuleExecutorRegistry(List<RuleExecutor> executorList) {
        for (RuleExecutor executor : executorList) {
            executors.put(executor.supports(), executor);
        }
    }

    public Optional<RuleExecutor> find(RuleExecutorType type) {
        return Optional.ofNullable(type == null ? null : executors.get(type));
    }

    public RuleExecutor get(RuleExecutorType type) {
        return find(type).orElseThrow(() -> new IllegalArgumentException("Unsupported rule executor: " + type));
    }

    public Map<String, Object> schemas() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        executors.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> result.put(e.getKey().name(), e.getValue().schema()));
        return result;
    }
}
