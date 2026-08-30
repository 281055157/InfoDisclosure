package com.example.disclosurereview.governance;

import com.example.disclosurereview.governance.agent.GovernanceAgentOrchestrator;
import com.example.disclosurereview.governance.domain.*;
import com.example.disclosurereview.governance.messaging.GovernanceDispatcher;
import com.example.disclosurereview.governance.messaging.GovernanceEventService;
import com.example.disclosurereview.governance.messaging.GovernanceGroupConsumer;
import com.example.disclosurereview.governance.messaging.GovernanceGroupMessage;
import com.example.disclosurereview.governance.persistence.entity.*;
import com.example.disclosurereview.governance.persistence.repository.*;
import com.example.disclosurereview.persistence.entity.ReviewRuleDefinitionEntity;
import com.example.disclosurereview.persistence.entity.ReviewRuleVersionEntity;
import com.example.disclosurereview.persistence.repository.ReviewRuleDefinitionJpaRepository;
import com.example.disclosurereview.persistence.repository.ReviewRuleVersionJpaRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.direct.auto-startup=true",
        "feedback-governance.rabbitmq.pending-publish-enabled=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class GovernanceRabbitIntegrationTest {
    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired private GovernanceDispatcher dispatcher;
    @Autowired private GovernanceGroupConsumer consumer;
    @Autowired private RuleGovernanceRunJpaRepository runRepository;
    @Autowired private RuleFeedbackGovernanceGroupJpaRepository groupRepository;
    @Autowired private RuleGovernanceEventJpaRepository eventRepository;
    @Autowired private ReviewRuleDefinitionJpaRepository definitionRepository;
    @Autowired private ReviewRuleVersionJpaRepository versionRepository;
    @MockBean private GovernanceAgentOrchestrator orchestrator;

    @Test
    void governanceGroupMessageIsConsumedOnceAndCompletesOutboxEvent() {
        var fixture = fixture();
        when(orchestrator.analyze(fixture.run().getId(), fixture.group().getId()))
                .thenReturn(new GovernanceAgentOrchestrator.AnalysisResult(99L, "mock-provider", "mock-model", 3));

        assertThat(dispatcher.dispatch(fixture.run().getId(), fixture.group().getId())).isTrue();

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            var events = eventRepository.findByGovernanceRun_IdOrderByCreatedAtAsc(fixture.run().getId());
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getEventStatus()).isEqualTo(GovernanceEventService.COMPLETED);
                assertThat(event.getCompletedAt()).isNotNull();
            });
        });
        verify(orchestrator, timeout(5_000).times(1)).analyze(fixture.run().getId(), fixture.group().getId());
        var completed = eventRepository.findByGovernanceRun_IdOrderByCreatedAtAsc(fixture.run().getId()).get(0);
        consumer.consume(new GovernanceGroupMessage(completed.getId(), fixture.run().getId(), fixture.group().getId(), 1));
        verifyNoMoreInteractions(orchestrator);
    }

    private Fixture fixture() {
        Instant now = Instant.now();
        ReviewRuleDefinitionEntity definition = new ReviewRuleDefinitionEntity();
        definition.setRuleCode("GOV_RABBIT_TEST_" + now.toEpochMilli()); definition.setRuleName("治理消息测试规则");
        definition.setRuleType("REGEX"); definition.setRuleCategory("REGEX"); definition.setVersionCode("v1");
        definition.setEnabled(true); definition.setCreatedAt(now); definition.setUpdatedAt(now);
        definition = definitionRepository.save(definition);

        ReviewRuleVersionEntity version = new ReviewRuleVersionEntity();
        version.setRuleDefinition(definition); version.setVersionCode(definition.getRuleCode() + ":v1"); version.setVersionNumber(1);
        version.setExecutorType("REGEX"); version.setScopeJson("{}"); version.setConditionJson("{\"patterns\":[\"TEST\"]}");
        version.setActionJson("{}"); version.setPromptJson("{}"); version.setStatus("PUBLISHED"); version.setActive(true);
        version.setCreatedAt(now); version.setUpdatedAt(now); version = versionRepository.save(version);

        RuleGovernanceRunEntity run = new RuleGovernanceRunEntity();
        run.setRunNo("RGR-TEST-" + now.toEpochMilli()); run.setTriggerType(GovernanceRunTriggerType.MANUAL);
        run.setStatus(GovernanceRunStatus.RUNNING); run.setStartedAt(now); run.setCreatedGroupCount(1);
        run.setCreatedAt(now); run.setUpdatedAt(now); run = runRepository.save(run);

        RuleFeedbackGovernanceGroupEntity group = new RuleFeedbackGovernanceGroupEntity();
        group.setGroupKey(definition.getRuleCode() + "|1|PROTOCOL|TEST|FALSE_POSITIVE");
        group.setRuleDefinition(definition); group.setRuleCode(definition.getRuleCode()); group.setRuleVersionEntity(version);
        group.setRuleVersion("v1"); group.setFeedbackType("FALSE_POSITIVE"); group.setDocumentCategory("PROTOCOL");
        group.setDeclaredFileType("TEST"); group.setStatus(GovernanceGroupStatus.PENDING); group.setFeedbackCount(3);
        group.setGovernanceRun(run); group.setLatestFeedbackAt(now); group.setCreatedAt(now); group.setUpdatedAt(now);
        group = groupRepository.save(group);
        return new Fixture(run, group);
    }

    private record Fixture(RuleGovernanceRunEntity run, RuleFeedbackGovernanceGroupEntity group) {}
}
