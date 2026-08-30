package com.example.disclosurereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 代销协议中的机构角色识别结果。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgencyAssessment(
        @JsonProperty("isDistributionAgreement")
        boolean isDistributionAgreement,
        boolean targetBankIsDistributor,
        String institutionName,
        String role,
        Double confidence,
        List<Evidence> evidence
) {
    public AgencyAssessment {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static AgencyAssessment empty(boolean distributionAgreement) {
        return new AgencyAssessment(distributionAgreement, false, null, null, 0.0, List.of());
    }
}
