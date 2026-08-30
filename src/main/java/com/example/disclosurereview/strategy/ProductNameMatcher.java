package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.MatchBasis;
import com.example.disclosurereview.model.Product;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 基于标准名称、别名、系列名和机构名的可解释轻量匹配器。 */
@Component
public class ProductNameMatcher {

    private final ProductNameNormalizer normalizer;
    private final ProductSeriesExtractor seriesExtractor;

    public ProductNameMatcher(ProductNameNormalizer normalizer, ProductSeriesExtractor seriesExtractor) {
        this.normalizer = normalizer;
        this.seriesExtractor = seriesExtractor;
    }

    public MatchResult match(Product product, String text, String fallbackName) {
        if (!StringUtils.hasText(text)) {
            return MatchResult.none();
        }
        String normalizedText = normalizer.normalize(text);
        List<MatchBasis> bases = new ArrayList<>();
        double confidence = 0.0;

        if (product != null) {
            if (contains(normalizedText, product.productName())) {
                bases.add(MatchBasis.EXACT_PRODUCT_NAME);
                confidence = Math.max(confidence, 0.9);
            }
            for (String alias : product.safeAliases()) {
                if (contains(normalizedText, alias)) {
                    bases.add(MatchBasis.PRODUCT_NAME_ALIAS);
                    confidence = Math.max(confidence, 0.82);
                }
            }
            for (String series : seriesExtractor.seriesCandidates(product, product.productName())) {
                if (series.length() >= 4 && normalizedText.contains(series)) {
                    bases.add(MatchBasis.PRODUCT_SERIES_NAME);
                    confidence = Math.max(confidence, 0.74);
                }
            }
            if (seriesOverlap(product, text)) {
                bases.add(MatchBasis.PRODUCT_SERIES_NAME);
                confidence = Math.max(confidence, 0.72);
            }
            if (contains(normalizedText, product.managerName())) {
                bases.add(MatchBasis.MANAGER_NAME);
                confidence = Math.max(confidence, 0.55);
            }
            if (contains(normalizedText, product.issuerName())) {
                bases.add(MatchBasis.ISSUER_NAME);
                confidence = Math.max(confidence, 0.55);
            }
        }

        if (StringUtils.hasText(fallbackName) && contains(normalizedText, fallbackName)) {
            bases.add(MatchBasis.PRODUCT_NAME_SEMANTIC);
            confidence = Math.max(confidence, 0.68);
        }

        return new MatchResult(bases.stream().distinct().toList(), confidence);
    }

    public boolean likelySameSeries(Product product, String text) {
        return match(product, text, null).bases().contains(MatchBasis.PRODUCT_SERIES_NAME);
    }

    private boolean contains(String normalizedText, String value) {
        return StringUtils.hasText(value) && normalizedText.contains(normalizer.normalize(value));
    }

    private boolean seriesOverlap(Product product, String text) {
        List<String> textSeries = seriesExtractor.seriesFromText(text);
        if (textSeries.isEmpty()) {
            return false;
        }
        List<String> productCores = new ArrayList<>();
        productCores.add(normalizer.coreName(product.productName()));
        product.safeAliases().forEach(a -> productCores.add(normalizer.coreName(a)));
        product.safeSeriesNames().forEach(s -> productCores.add(normalizer.coreName(s)));
        for (String textTerm : textSeries) {
            for (String productCore : productCores) {
                if (textTerm.length() >= 4 && productCore.length() >= 4
                        && (productCore.contains(textTerm)
                        || textTerm.contains(productCore)
                        || longestCommonSubstring(textTerm, productCore) >= 6)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int longestCommonSubstring(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    best = Math.max(best, dp[i][j]);
                }
            }
        }
        return best;
    }

    public record MatchResult(List<MatchBasis> bases, double confidence) {
        public static MatchResult none() {
            return new MatchResult(List.of(), 0.0);
        }

        public boolean matched() {
            return !bases.isEmpty();
        }
    }
}
