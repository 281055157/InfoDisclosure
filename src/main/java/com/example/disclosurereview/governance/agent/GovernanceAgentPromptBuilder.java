package com.example.disclosurereview.governance.agent;

import com.example.disclosurereview.governance.tool.GovernanceAgentToolRegistry;
import com.example.disclosurereview.governance.tool.GovernanceRuleCandidateContract;
import com.example.disclosurereview.governance.tool.ToolExecutionResult;
import com.example.disclosurereview.llm.LlmToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class GovernanceAgentPromptBuilder {
    private static final int MAX_HISTORY_CHARS = 12_000;
    private static final int MAX_ENTRY_CHARS = 3_000;
    private final GovernanceAgentToolRegistry registry;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    public GovernanceAgentPromptBuilder(GovernanceAgentToolRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
        this.systemPrompt = load();
    }

    public String systemPrompt() { return systemPrompt; }

    public String structuredUserPrompt(Long runId, Long groupId, List<HistoryEntry> history) {
        return """
                当前治理运行 ID：%d
                当前治理分组 ID：%d
                ID 速查：propose* 使用 governanceGroupId=%d；runRuleBacktest/estimateAffectedDocuments 使用 feedbackGroupId=%d。仅 RULE_CORRECTION 可用 compareRuleVersions（analysis_brief.group.ruleVersionId）及 getRuleVersion/getRuleExecutionRecords（analysis_brief.group.ruleVersionNumber）；RULE_GAP 没有来源规则版本，禁止调用这些 Tool。

                Tool 使用摘要：
                %s

                已完成的交互历史（按预算压缩，最近结果优先）：
                %s

                现在只返回一个 JSON 对象，不要 Markdown：
                调用一个或多个 Tool 时：
                {"thoughtSummary":"简短分析","nextAction":"CALL_TOOLS","toolCalls":[{"callId":"本轮唯一ID","toolName":"工具名","arguments":{}}]}
                如果已经通过 propose* Tool 创建提案才可返回：
                {"thoughtSummary":"简短总结","nextAction":"FINISH"}
                同一轮中参数已经齐备、彼此不依赖的只读/校验 Tool 应放入同一个 toolCalls 数组，系统会并行执行并一次性返回全部结果。
                依赖前一个 Tool 输出的调用必须放到下一轮；propose* 必须单独一轮调用，不得与其他 Tool 混合。
                """.formatted(runId, groupId, groupId, groupId, structuredToolGuide(), compactHistory(history));
    }

    public String initialNativePrompt(Long runId, Long groupId) {
        return "治理运行 ID=" + runId + "，治理分组 ID=" + groupId
                + "。系统已自动执行 getGovernanceAnalysisBrief，并在本消息的 analysis_brief 中提供结果，请勿重复调用。"
                + "请使用 Tool 完成必要校验和回测；同一轮可并行请求多个彼此独立的 Tool，"
                + "依赖前序结果的调用必须留到下一轮，propose* 必须单独调用。最终必须调用一个 propose* Tool。";
    }

    public List<LlmToolDefinition> nativeTools() {
        return registry.definitions().stream()
                .map(definition -> new LlmToolDefinition(definition.name(), definition.description(), definition.inputSchema()))
                .toList();
    }

    private String load() {
        try (var in = new ClassPathResource("prompts/feedback-governance-system-prompt.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载反馈治理 Agent Prompt", e);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { return "[]"; }
    }

    private String structuredToolGuide() {
        return """
                LangGraph 的 ANALYSIS_BRIEF 节点已经自动执行 getGovernanceAnalysisBrief，并把结果放在历史的 analysis_brief 中；不要重复调用它。
                analysis_brief 中已经包含 group、ruleDefinition、sourceRuleVersion、反馈样本、执行记录和历史治理记录；不要重复获取其中已有的信息。
                仅在摘要缺少必要细节时才调用 getFeedbackGroup/getFeedbackSamples/getRuleDefinition/getRuleVersion/getRuleExecutionRecords 等单项读取 Tool。
                RULE_CORRECTION 候选规则（同一 candidateRule 不可变）：validateRuleConfig → compareRuleVersions(sourceRuleVersionId,candidateRule) → checkRuleConflict → runRuleBacktest(feedbackGroupId,candidateRule) → estimateAffectedDocuments(feedbackGroupId,candidateRule)。
                RULE_GAP 表示漏报且没有来源规则：必须以 creatingRule=true 校验新的 candidateRule，禁止调用 compareRuleVersions；规则类提案只能调用 proposeRuleCreate，不能更新、停用或创建例外。
                validate 完成后，适用的 compareRuleVersions、checkRuleConflict、runRuleBacktest、estimateAffectedDocuments 以及 REGEX 的 compileRegex
                应在参数齐备时作为同一批 toolCalls 并行调用。
                runRuleBacktest 会在内部自动完成 LLM_POLICY/HYBRID 的批量模型回测、降级与证据验证，不要自行寻找或调用额外模型 Tool。HYBRID 的 candidateRule.condition.locator 必须明确填写可执行的确定性定位规则编码。
                回测返回 UNAVAILABLE、可判定样本不足或 llmCallCount=0 时不算完成规则变更前置校验，应改为暂缓、NO_ACTION 或优化建议，不得把“环境禁用”当作成功回测。
                仅在必需证据和同一候选的所有校验完成后，调用一个匹配根因的 propose* Tool。
                规则变更类 proposeRuleUpdate/proposeRuleDisable/proposeRuleCreate/proposeRuleException 必填 governanceGroupId/rootCauseType/problemSummary/rootCauseAnalysis/changeReason/candidateRule/expectedEffect/riskDescription/agentConfidence。
                proposeOptimizationAdvice 必填 governanceGroupId/rootCauseType/problemSummary/rootCauseAnalysis/optimizationCategory/optimizationAdvice/agentConfidence。
                需要停用旧规则并新建替代规则时，优先调用 proposeCompositeRuleChange，actions 按顺序放 DISABLE_RULE 和 CREATE_RULE。
                复合提案的每个 action 都是独立候选，必须分别生成 candidateHash 和完整服务端产物，不能用 CREATE_RULE 的产物代替 DISABLE_RULE。
                推荐固定批次：先 validate CREATE_RULE；下一轮补 runRuleBacktest/checkRuleConflict/estimateAffectedDocuments；
                再 validate DISABLE_RULE；下一轮补 runRuleBacktest/checkRuleConflict/estimateAffectedDocuments/compareRuleVersions/compileRegex；最后单独 proposeCompositeRuleChange。
                proposeCompositeRuleChange 若返回 MISSING_COMPOSITE_ARTIFACTS，只补 actions[].missingTools 指定的动作和 Tool，保持对应 candidateRule 完全不变。
                若 tool_batch 返回 deferredToolCalls，下一轮只按原参数调用这些延期 Tool，不要重新执行已经成功的 Tool。
                禁止使用旧字段 groupId/rootCause/advice/significance/estimatedEffort/proposedAction 调用 propose*；这些字段会被拒绝。
                candidateRule 只接受根级字段 ruleCode/ruleName/executorType/scope/condition/action/prompt/priority/enabled。
                prompt 必须与 condition/action 同级；绝不能放在 condition 内，也不要使用持久化字段名 promptJson。
                LLM_POLICY 候选规则必须包含根级 candidateRule.prompt.reviewGoal，并用字符串 candidateRule.prompt.criteria 明确区分正向保本承诺与“非保本、不承诺、并不保证”等否定语境。
                validateRuleConfig 返回的配置错误只表示候选 JSON 需要修复，不表示 LLM 调用失败或替代规则不可建立；应按 repairHint/minimalValidCandidateRule 修复同一候选，不能因此降级为停用或纯建议提案。
                thoughtSummary 只写业务判断，不要复述“历史尝试因参数错误失败”。
                LLM_POLICY 完整合法 candidateRule 示例（字段层级必须照此组织）：
                %s
                注册 Tool 定义与输入 schema：
                %s
                复合提案最小形状：
                {"toolName":"proposeCompositeRuleChange","arguments":{"governanceGroupId":当前分组ID,"rootCauseType":"RULE_EXECUTOR","problemSummary":"...","rootCauseAnalysis":"...","changeReason":"...","expectedEffect":"...","riskDescription":"...","agentConfidence":0.8,"actions":[{"actionType":"DISABLE_RULE","ruleCode":"源规则编码","sourceRuleVersionId":源规则版本ID,"candidateRule":完整源规则但enabled=false},{"actionType":"CREATE_RULE","candidateRule":完整LLM_POLICY新规则}]}}
                """.formatted(json(GovernanceRuleCandidateContract.llmPolicyExample(mapper)),
                json(registry.definitions())).replaceAll("\\s+", " ").strip();
    }

    private String compactHistory(List<HistoryEntry> history) {
        List<HistoryEntry> reversed = new ArrayList<>(history == null ? List.of() : history);
        Collections.reverse(reversed);
        List<HistoryEntry> kept = new ArrayList<>();
        int remaining = MAX_HISTORY_CHARS;
        for (HistoryEntry entry : reversed) {
            String rendered = json(entry.content());
            if (rendered.length() > MAX_ENTRY_CHARS) rendered = rendered.substring(0, MAX_ENTRY_CHARS) + "…";
            if (rendered.length() > remaining && !kept.isEmpty()) break;
            if (rendered.length() > remaining) rendered = rendered.substring(0, Math.max(0, remaining)) + "…";
            Object content = json(entry.content()).length() <= Math.min(MAX_ENTRY_CHARS, remaining)
                    ? entry.content() : rendered;
            kept.add(new HistoryEntry(entry.role(), entry.toolName(), content));
            remaining -= rendered.length();
            if (remaining <= 0) break;
        }
        Collections.reverse(kept);
        return json(kept);
    }

    public record HistoryEntry(String role, String toolName, Object content) implements java.io.Serializable {}
}
