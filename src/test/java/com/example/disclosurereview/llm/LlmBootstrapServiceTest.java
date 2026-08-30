package com.example.disclosurereview.llm;

import com.example.disclosurereview.config.LlmProperties;
import com.example.disclosurereview.persistence.entity.LlmModelConfigEntity;
import com.example.disclosurereview.persistence.entity.LlmProviderConfigEntity;
import com.example.disclosurereview.persistence.repository.LlmModelConfigJpaRepository;
import com.example.disclosurereview.persistence.repository.LlmProviderConfigJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmBootstrapServiceTest {

    @Test
    void appliesKeylessIntranetModelAndDisablesSeededPublicModels() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://localhost:11434/v1/");
        properties.setModel("intranet-qwen");
        properties.getBootstrap().setEnabled(true);
        properties.getBootstrap().setApiKeyEnv("");

        LlmProviderConfigJpaRepository providers = mock(LlmProviderConfigJpaRepository.class);
        LlmModelConfigJpaRepository models = mock(LlmModelConfigJpaRepository.class);
        when(providers.findByProviderCode("intranet-default")).thenReturn(Optional.empty());
        when(providers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(models.findByModelCode("intranet-primary")).thenReturn(Optional.empty());
        LlmModelConfigEntity publicModel = new LlmModelConfigEntity();
        publicModel.setModelCode("deepseek-v4-flash-primary");
        publicModel.setEnabled(true);
        when(models.findAll()).thenReturn(List.of(publicModel));
        when(models.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new LlmBootstrapService(properties, providers, models).applyConfiguration();

        ArgumentCaptor<LlmProviderConfigEntity> providerCaptor = ArgumentCaptor.forClass(LlmProviderConfigEntity.class);
        verify(providers).save(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getBaseUrl()).isEqualTo("http://localhost:11434/v1");

        ArgumentCaptor<LlmModelConfigEntity> modelCaptor = ArgumentCaptor.forClass(LlmModelConfigEntity.class);
        verify(models).save(modelCaptor.capture());
        assertThat(modelCaptor.getValue().getModelName()).isEqualTo("intranet-qwen");
        assertThat(modelCaptor.getValue().getApiKeyEnv()).isEmpty();
        assertThat(publicModel.isEnabled()).isFalse();
    }
}
