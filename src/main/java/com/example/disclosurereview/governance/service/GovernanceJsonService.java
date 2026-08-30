package com.example.disclosurereview.governance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class GovernanceJsonService {
    private final ObjectMapper mapper;

    public GovernanceJsonService(ObjectMapper mapper) { this.mapper = mapper; }

    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("无法序列化治理数据", e); }
    }

    public JsonNode tree(String value) {
        try { return mapper.readTree(value == null || value.isBlank() ? "{}" : value); }
        catch (Exception e) { throw new IllegalArgumentException("治理 JSON 格式无效: " + e.getMessage(), e); }
    }

    public String hash(Object value) {
        try {
            JsonNode tree = value instanceof JsonNode node ? node : mapper.valueToTree(value);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(tree));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算治理对象哈希", e);
        }
    }

    public String hashText(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
