package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 产品库中的一条产品记录 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Product(
        String productCode,
        String productName,
        List<String> aliases,
        String managerName,
        String issuerName,
        String parentProductCode,
        List<String> shareCodes,
        List<String> codeAliases,
        List<String> seriesNames,
        List<String> distributorNames,
        String productType
) {
    public Product(String productCode,
                   String productName,
                   List<String> aliases,
                   String productType) {
        this(productCode, productName, aliases, null, null, null,
                List.of(), List.of(), List.of(), List.of(), productType);
    }

    public List<String> safeAliases() {
        return aliases == null ? List.of() : aliases;
    }

    public List<String> safeShareCodes() {
        return shareCodes == null ? List.of() : shareCodes;
    }

    public List<String> safeCodeAliases() {
        return codeAliases == null ? List.of() : codeAliases;
    }

    public List<String> safeSeriesNames() {
        return seriesNames == null ? List.of() : seriesNames;
    }

    public List<String> safeDistributorNames() {
        return distributorNames == null ? List.of() : distributorNames;
    }
}
