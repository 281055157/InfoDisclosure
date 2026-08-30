package com.example.disclosurereview.strategy;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** 产品名称与系列名称的轻量规范化工具。 */
@Component
public class ProductNameNormalizer {

    private static final List<String> DOCUMENT_SUFFIXES = List.of(
            "产品说明书", "投资协议书", "风险揭示书", "客户权益须知", "投资者权益须知",
            "发行公告", "成立公告", "定期公告", "定期报告", "到期公告", "兑付公告",
            "净值公告", "产品净值公告", "其他公告", "临时公告");

    private static final List<String> GENERIC_WORDS = List.of(
            "理财产品", "产品", "风险揭示书", "投资者权益须知", "客户权益须知", "份额");

    public String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return normalized
                .replace('（', '(')
                .replace('）', ')')
                .replace('【', '[')
                .replace('】', ']')
                .replaceAll("\\s+", "")
                .strip();
    }

    public String removeDocumentSuffix(String text) {
        String value = normalize(text);
        for (String suffix : DOCUMENT_SUFFIXES) {
            value = value.replace(normalize(suffix), "");
        }
        return value;
    }

    public String coreName(String text) {
        String value = removeDocumentSuffix(text);
        for (String word : GENERIC_WORDS) {
            value = value.replace(normalize(word), "");
        }
        return value;
    }

    public List<String> bracketTerms(String text) {
        String value = normalize(text);
        List<String> result = new ArrayList<>();
        int start = value.indexOf('[');
        while (start >= 0) {
            int end = value.indexOf(']', start + 1);
            if (end > start + 1) {
                result.add(value.substring(start + 1, end));
                start = value.indexOf('[', end + 1);
            } else {
                break;
            }
        }
        return result;
    }
}
