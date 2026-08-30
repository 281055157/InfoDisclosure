package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import com.example.disclosurereview.persistence.entity.LlmProviderConfigEntity;
import com.example.disclosurereview.persistence.repository.LlmModelConfigJpaRepository;
import com.example.disclosurereview.persistence.repository.LlmProviderConfigJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Applies one deployment-owned model configuration after Flyway/JPA initialization.
 * It is disabled for ordinary development and enabled by the offline Compose package.
 */
@Component
public class LlmBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LlmBootstrapService.class);

    private final LlmProperties properties;
    private final LlmProviderConfigJpaRepository providerRepository;
    private final LlmModelConfigJpaRepository modelRepository;

    public LlmBootstrapService(LlmProperties properties,
                               LlmProviderConfigJpaRepository providerRepository,
                               LlmModelConfigJpaRepository modelRepository) {
        this.properties = properties;
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        applyConfiguration();
    }

    void applyConfiguration() {
        LlmProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (bootstrap == null || !bootstrap.isEnabled()) {
            return;
        }
        String baseUrl = required(properties.getBaseUrl(), "LLM_BASE_URL");
        String modelName = required(properties.getModel(), "LLM_MODEL");
        String providerCode = required(bootstrap.getProviderCode(), "LLM_BOOTSTRAP_PROVIDER_CODE");
        String modelCode = required(bootstrap.getModelCode(), "LLM_BOOTSTRAP_MODEL_CODE");
        Instant now = Instant.now();

        LlmProviderConfigEntity provider = providerRepository.findByProviderCode(providerCode)
                .orElseGet(LlmProviderConfigEntity::new);
        provider.setProviderCode(providerCode);
        provider.setProviderType(defaultText(bootstrap.getProviderType(), "OPENAI_COMPATIBLE"));
        provider.setBaseUrl(trimTrailingSlash(baseUrl));
        provider.setEnabled(true);
        if (provider.getCreatedAt() == null) {
            provider.setCreatedAt(now);
        }
        provider.setUpdatedAt(now);
        provider = providerRepository.save(provider);

        LlmModelConfigEntity model = modelRepository.findByModelCode(modelCode)
                .orElseGet(LlmModelConfigEntity::new);
        model.setProvider(provider);
        model.setModelCode(modelCode);
        model.setModelName(modelName);
        model.setPriority(bootstrap.getPriority());
        model.setEnabled(properties.isEnabled());
        model.setTimeoutSeconds((int) Math.max(1, properties.getTimeout().toSeconds()));
        model.setMaxRetries(Math.max(0, bootstrap.getMaxRetries()));
        model.setTemperature(properties.getTemperature());
        model.setResponseFormat(bootstrap.getResponseFormat() == null ? "" : bootstrap.getResponseFormat().strip());
        model.setApiKeyEnv(bootstrap.getApiKeyEnv() == null ? "" : bootstrap.getApiKeyEnv().strip());
        if (model.getCreatedAt() == null) {
            model.setCreatedAt(now);
        }
        model.setUpdatedAt(now);

        if (bootstrap.isDisableOtherModels()) {
            List<LlmModelConfigEntity> otherModels = modelRepository.findAll().stream()
                    .filter(existing -> !modelCode.equals(existing.getModelCode()))
                    .filter(LlmModelConfigEntity::isEnabled)
                    .toList();
            otherModels.forEach(existing -> {
                existing.setEnabled(false);
                existing.setUpdatedAt(now);
            });
            if (!otherModels.isEmpty()) {
                modelRepository.saveAll(otherModels);
            }
        }
        modelRepository.save(model);
        log.info("Applied deployment LLM model, provider={}, baseUrl={}, model={}, authMode={}",
                providerCode, provider.getBaseUrl(), modelName,
                StringUtils.hasText(model.getApiKeyEnv()) ? "API_KEY_ENV" : "NONE");
    }

    private String required(String value, String setting) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(setting + " must be configured when LLM bootstrap is enabled");
        }
        return value.strip();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private String trimTrailingSlash(String value) {
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
