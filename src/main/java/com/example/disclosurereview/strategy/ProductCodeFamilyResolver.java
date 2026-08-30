package com.example.disclosurereview.strategy;

import com.example.disclosurereview.model.Product;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 解析产品代码、份额代码和代码别名之间的可解释关系。 */
@Component
public class ProductCodeFamilyResolver {

    public List<String> targetCodes(String declaredProductCode, Product product) {
        Set<String> result = new LinkedHashSet<>();
        add(result, declaredProductCode);
        if (product != null) {
            add(result, product.productCode());
            add(result, product.parentProductCode());
            addAll(result, product.safeShareCodes());
            addAll(result, product.safeCodeAliases());
        }
        return result.stream().toList();
    }

    public boolean isExactTargetCode(String code, String declaredProductCode, Product product) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String c = code.strip();
        return targetCodes(declaredProductCode, product).stream().anyMatch(c::equals);
    }

    public boolean isSameFamily(String code, String declaredProductCode, Product product) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(declaredProductCode)) {
            return false;
        }
        String c = code.strip();
        String declared = declaredProductCode.strip();
        if (isExactTargetCode(c, declared, product)) {
            return true;
        }
        if (product != null && StringUtils.hasText(product.parentProductCode())) {
            return c.startsWith(product.parentProductCode()) || declared.startsWith(product.parentProductCode());
        }
        String stable = stablePrefix(declared);
        return stable.length() >= 6 && c.startsWith(stable);
    }

    public String stablePrefix(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        String value = code.strip();
        int end = value.length();
        while (end > 0 && Character.isLetter(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private void addAll(Set<String> result, List<String> values) {
        if (values == null) {
            return;
        }
        values.forEach(v -> add(result, v));
    }

    private void add(Set<String> result, String value) {
        if (StringUtils.hasText(value)) {
            result.add(value.strip());
        }
    }
}
