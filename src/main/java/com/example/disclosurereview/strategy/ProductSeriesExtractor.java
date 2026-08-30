package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.Product;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 从产品主数据、文件名或标题中提取可用于系列匹配的候选词。 */
@Component
public class ProductSeriesExtractor {

    private final ProductNameNormalizer normalizer;

    public ProductSeriesExtractor(ProductNameNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<String> seriesCandidates(Product product, String declaredTypeText) {
        Set<String> result = new LinkedHashSet<>();
        if (product != null) {
            addAll(result, product.safeSeriesNames());
            add(result, product.productName());
            addAll(result, product.safeAliases());
        }
        addAll(result, normalizer.bracketTerms(declaredTypeText));
        return result.stream()
                .map(normalizer::coreName)
                .filter(s -> s.length() >= 4)
                .toList();
    }

    public List<String> seriesFromText(String text) {
        List<String> terms = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return terms;
        }
        terms.addAll(normalizer.bracketTerms(text));
        return terms.stream()
                .map(normalizer::coreName)
                .filter(s -> s.length() >= 4)
                .toList();
    }

    private void addAll(Set<String> result, List<String> values) {
        if (values == null) {
            return;
        }
        values.forEach(v -> add(result, v));
    }

    private void add(Set<String> result, String value) {
        if (StringUtils.hasText(value)) {
            result.add(value);
        }
    }
}
