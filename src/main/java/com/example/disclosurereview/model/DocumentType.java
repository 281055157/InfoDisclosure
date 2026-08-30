package com.example.disclosurereview.model;

import java.util.Arrays;
import java.util.List;

/** 文件类型路由枚举，英文值用于策略选择，中文别名用于声明值和正文候选类型归一化。 */
public enum DocumentType {
    PRODUCT_DESCRIPTION("产品说明书", List.of("产品说明书")),
    RISK_DISCLOSURE("风险揭示书", List.of("风险揭示书")),
    DISTRIBUTION_AGREEMENT("代销协议书", List.of("代销协议书", "理财代销协议书", "代销协议", "代理销售协议")),
    INVESTMENT_AGREEMENT("投资协议书", List.of("投资协议书", "投资协议")),
    CUSTOMER_RIGHTS_NOTICE("客户权益须知", List.of("客户权益须知", "投资者权益须知", "投资人权益须知")),
    ISSUANCE_ANNOUNCEMENT("发行公告", List.of("发行公告", "成立公告", "产品成立公告", "产品发行公告")),
    PERIODIC_ANNOUNCEMENT("定期公告", List.of("定期公告", "定期报告")),
    MATURITY_ANNOUNCEMENT("到期公告", List.of("到期公告", "兑付公告", "产品到期公告")),
    NAV_ANNOUNCEMENT("净值公告", List.of("净值公告", "产品净值公告")),
    OTHER_ANNOUNCEMENT("其他公告", List.of("其他公告", "临时公告")),
    UNKNOWN("UNKNOWN", List.of("UNKNOWN", "无法判断"));

    private final String displayName;
    private final List<String> defaultAliases;

    DocumentType(String displayName, List<String> defaultAliases) {
        this.displayName = displayName;
        this.defaultAliases = defaultAliases;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> defaultAliases() {
        return defaultAliases;
    }

    public boolean allowsMultipleProducts() {
        return this == PERIODIC_ANNOUNCEMENT
                || this == MATURITY_ANNOUNCEMENT
                || this == NAV_ANNOUNCEMENT
                || this == OTHER_ANNOUNCEMENT;
    }

    public boolean isSingleProductStrict() {
        return this == PRODUCT_DESCRIPTION
                || this == INVESTMENT_AGREEMENT
                || this == ISSUANCE_ANNOUNCEMENT;
    }

    public static List<DocumentType> supportedTypes() {
        return Arrays.stream(values())
                .filter(t -> t != UNKNOWN)
                .toList();
    }
}
