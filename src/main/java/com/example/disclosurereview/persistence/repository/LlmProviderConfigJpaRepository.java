package com.example.disclosurereview.persistence.repository;

import com.example.disclosurereview.persistence.entity.LlmProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmProviderConfigJpaRepository extends JpaRepository<LlmProviderConfigEntity, Long> {

    Optional<LlmProviderConfigEntity> findByProviderCode(String providerCode);
}
