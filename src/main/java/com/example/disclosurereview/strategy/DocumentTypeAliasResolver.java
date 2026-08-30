package com.example.disclosurereview.strategy;

import com.example.disclosurereview.config.ReviewProperties;
import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.DocumentType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 将文件名、B9 或正文中的中文别名归一到 DocumentType。 */
@Component
public class DocumentTypeAliasResolver {

    private final ReviewProperties reviewProperties;

    public DocumentTypeAliasResolver(ReviewProperties reviewProperties) {
        this.reviewProperties = reviewProperties;
    }

    public DocumentType resolve(String value) {
        if (!StringUtils.hasText(value)) {
            return DocumentType.UNKNOWN;
        }
        String normalized = normalize(value);
        for (Map.Entry<DocumentType, List<String>> entry : aliases().entrySet()) {
            DocumentType type = entry.getKey();
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
            for (String alias : entry.getValue()) {
                String a = normalize(alias);
                if (normalized.equals(a) || normalized.contains(a)) {
                    return type;
                }
            }
        }
        return DocumentType.UNKNOWN;
    }

    public DocumentType detectFromPages(List<DocumentPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return DocumentType.UNKNOWN;
        }
        String head = pages.stream()
                .limit(3)
                .map(DocumentPage::normalizedText)
                .filter(StringUtils::hasText)
                .reduce("", (a, b) -> a + "\n" + b);
        if (!StringUtils.hasText(head)) {
            return DocumentType.UNKNOWN;
        }
        List<Map.Entry<DocumentType, String>> aliasList = aliases().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(a -> Map.entry(e.getKey(), a)))
                .sorted(Comparator.comparingInt((Map.Entry<DocumentType, String> e) -> e.getValue().length()).reversed())
                .toList();
        String normalizedHead = normalize(head);
        for (Map.Entry<DocumentType, String> alias : aliasList) {
            if (normalizedHead.contains(normalize(alias.getValue()))) {
                return alias.getKey();
            }
        }
        return DocumentType.UNKNOWN;
    }

    public Map<DocumentType, List<String>> aliases() {
        Map<DocumentType, List<String>> result = new LinkedHashMap<>();
        for (DocumentType type : DocumentType.values()) {
            result.put(type, new ArrayList<>(type.defaultAliases()));
        }
        if (reviewProperties.getDocumentTypeAliases() != null) {
            reviewProperties.getDocumentTypeAliases().forEach((key, values) -> {
                DocumentType type = enumValue(key);
                if (type != DocumentType.UNKNOWN && values != null) {
                    result.computeIfAbsent(type, k -> new ArrayList<>()).addAll(values);
                }
            });
        }
        return result;
    }

    private DocumentType enumValue(String key) {
        if (!StringUtils.hasText(key)) {
            return DocumentType.UNKNOWN;
        }
        try {
            return DocumentType.valueOf(key.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DocumentType.UNKNOWN;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
