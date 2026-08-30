package com.example.disclosurereview.parser;

import com.example.disclosurereview.model.FileNameInfo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从文件名中提取产品代码和声明文件类型。
 * 基本格式：产品代码_附件类型.pdf
 * 规则：去掉扩展名与括号内容后，第一个下划线前为产品代码，其余部分为文件类型。
 */
@Component
public class FileNameParser {

    public FileNameInfo parse(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return new FileNameInfo(originalFileName, null, null);
        }
        String name = originalFileName.strip();

        // 去掉扩展名
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }

        // 去掉中英文括号及其内容（如 "SGN22555_投资协议书(盖章版)"）
        name = name.replaceAll("[（(][^）)]*[）)]", "").strip();

        // 处理下划线（含全角下划线），类型名中可能存在下划线，取第一个作为分隔
        int sep = firstSeparatorIndex(name);
        if (sep < 0) {
            return new FileNameInfo(originalFileName, nullIfBlank(name), null);
        }
        String productCode = name.substring(0, sep).strip();
        String type = name.substring(sep + 1).strip();
        return new FileNameInfo(originalFileName, nullIfBlank(productCode), nullIfBlank(type));
    }

    private int firstSeparatorIndex(String name) {
        int i1 = name.indexOf('_');
        int i2 = name.indexOf('＿');
        if (i1 < 0) {
            return i2;
        }
        if (i2 < 0) {
            return i1;
        }
        return Math.min(i1, i2);
    }

    private String nullIfBlank(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
