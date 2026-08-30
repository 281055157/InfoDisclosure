package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.DocumentPage;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ProductTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 对净值公告等表格文本做目标产品行的轻量定位。 */
@Component
public class ProductTableRowExtractor {

    private static final Pattern REGISTRATION_CODE = Pattern.compile("(Z[0-9A-Z]{8,})");
    private static final Pattern DATE = Pattern.compile("(20\\d{6})");
    private static final Pattern NAV = Pattern.compile("\\b(\\d+\\.\\d{3,6})\\b");

    private final ProductCodeFamilyResolver familyResolver;

    public ProductTableRowExtractor(ProductCodeFamilyResolver familyResolver) {
        this.familyResolver = familyResolver;
    }

    public List<ProductTableRow> extractTargetRows(List<DocumentPage> pages,
                                                   String declaredProductCode,
                                                   Product product) {
        if (!StringUtils.hasText(declaredProductCode) || pages == null) {
            return List.of();
        }
        List<ProductTableRow> rows = new ArrayList<>();
        for (DocumentPage page : pages) {
            String text = page.normalizedText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            int idx = text.indexOf(declaredProductCode);
            while (idx >= 0) {
                String context = context(text, idx, idx + declaredProductCode.length(), 160);
                rows.add(toRow(context, page.pageNumber(), declaredProductCode, product));
                idx = text.indexOf(declaredProductCode, idx + declaredProductCode.length());
            }
        }
        return rows.stream().distinct().toList();
    }

    private ProductTableRow toRow(String context, Integer pageNumber, String targetCode, Product product) {
        String productName = product == null ? null : product.productName();
        String registrationCode = firstMatch(REGISTRATION_CODE, context);
        String valuationDate = firstMatch(DATE, context);
        String nav = firstMatch(NAV, context);
        String salesCode = targetCode;
        String productCode = targetCode;
        if (product != null && StringUtils.hasText(product.productCode())
                && !familyResolver.isExactTargetCode(targetCode, product.productCode(), product)) {
            productCode = product.productCode();
        }
        return new ProductTableRow(productName, registrationCode, productCode, salesCode,
                valuationDate, nav, null, pageNumber, context, 0.86);
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String context(String text, int start, int end, int radius) {
        int from = Math.max(0, start - radius);
        int to = Math.min(text.length(), end + radius);
        return text.substring(from, to).replaceAll("\\s+", " ").strip();
    }
}
