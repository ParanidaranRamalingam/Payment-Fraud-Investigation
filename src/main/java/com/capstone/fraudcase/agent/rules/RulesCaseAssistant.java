package com.capstone.fraudcase.agent.rules;

import com.capstone.fraudcase.agent.common.CaseAssistant;
import com.capstone.fraudcase.agent.common.CaseAssistantReply;
import com.capstone.fraudcase.agent.common.CaseMessageContext;
import com.capstone.fraudcase.domain.CaseMessage;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.ProcessorMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic case assistant: answers from the case's persisted facts only, selected by keyword topics. The
 * analyst message is matched against fixed vocabularies and is never echoed, executed or treated as instructions,
 * so injected text cannot change classification, state or scope. Requests for actions are refused (advisory only)
 * and references to other records are ignored (no cross-case recall).
 */
@Component
public class RulesCaseAssistant implements CaseAssistant {

    enum Topic { STATUS, RECOMMENDATION, EVIDENCE, MISSING, CITATIONS }

    static final String ADVISORY_ONLY = "I am an advisory assistant and cannot take or trigger actions such as "
            + "refunds, credits, card blocks, fund movements, customer contact, compliance filings or case "
            + "decisions. A human reviewer must record the disposition through the review step.";
    static final String OTHER_RECORDS = "I can only use facts recorded for this case. References to other cases "
            + "or records are not available in this conversation and were ignored.";
    static final String HELP = "I can describe this case's status, latest recommendation, evidence (transaction, "
            + "device, location, merchant, prior disputes, SLA), missing information and policy citations.";
    static final int MAX_SUMMARY_CHARS = 280;

    private static final Map<Topic, Pattern> TOPICS = new LinkedHashMap<>();
    private static final Map<EvidenceTool, Pattern> TOOL_WORDS = new EnumMap<>(EvidenceTool.class);
    private static final Pattern ACTION = words("refund\\w*", "reimburs\\w*", "credit (?:the|their|back)",
            "chargeback\\w*", "block\\w*", "freez\\w*", "unfreez\\w*", "suspend\\w*", "transfer\\w*", "wire",
            "pay ?out\\w*", "release (?:the )?funds", "revers\\w*", "file (?:a|an|the)", "sar", "contact\\w*",
            "e-?mail\\w*", "phone", "call (?:the|them|him|her)", "notify\\w*", "approv\\w*", "authori[sz]e",
            "close (?:the|this) case", "mark (?:it|the case|this case) as");
    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    static {
        TOPICS.put(Topic.STATUS, words("status", "state", "stage", "progress", "next steps?", "where"));
        TOPICS.put(Topic.RECOMMENDATION, words("recommend\\w*", "classif\\w*", "risk\\w*", "route\\w*",
                "confidence", "rationale", "outcome", "verdict", "why", "summar\\w*"));
        TOPICS.put(Topic.EVIDENCE, words("evidence", "tools?", "signals?", "facts?", "device\\w*", "locations?",
                "geo\\w*", "merchant\\w*", "transactions?", "ledger", "disputes?", "sla", "deadline"));
        TOPICS.put(Topic.MISSING, words("missing", "gaps?", "incomplete", "unavailable", "timeouts?", "insufficient",
                "lack\\w*"));
        TOPICS.put(Topic.CITATIONS, words("polic\\w*", "citations?", "cite\\w*", "procedur\\w*", "guidance"));
        TOOL_WORDS.put(EvidenceTool.TRANSACTION_DETAILS, words("transactions?", "ledger"));
        TOOL_WORDS.put(EvidenceTool.DEVICE_SIGNALS, words("device\\w*"));
        TOOL_WORDS.put(EvidenceTool.RECENT_LOCATIONS, words("locations?", "geo\\w*"));
        TOOL_WORDS.put(EvidenceTool.MERCHANT_RISK_PROFILE, words("merchant\\w*"));
        TOOL_WORDS.put(EvidenceTool.PRIOR_DISPUTES, words("disputes?"));
        TOOL_WORDS.put(EvidenceTool.CASE_SLA, words("sla", "deadline"));
    }

    private static Pattern words(String... alternatives) {
        return Pattern.compile("\\b(?:" + String.join("|", alternatives) + ")\\b");
    }

    @Override
    public CaseAssistantReply reply(CaseMessageContext context) {
        Objects.requireNonNull(context, "context");
        String text = context.message().toLowerCase(Locale.ROOT);
        Set<String> cited = new LinkedHashSet<>();
        List<String> sections = new ArrayList<>();

        if (referencesOtherRecords(context)) {
            sections.add(OTHER_RECORDS);
        }
        if (ACTION.matcher(text).find()) {
            sections.add(ADVISORY_ONLY);
        }
        Set<Topic> topics = EnumSet.noneOf(Topic.class);
        TOPICS.forEach((topic, pattern) -> {
            if (pattern.matcher(text).find()) {
                topics.add(topic);
            }
        });
        if (topics.isEmpty()) {
            sections.add(HELP);
            topics.add(Topic.STATUS);
        }
        for (Topic topic : topics) {
            sections.add(switch (topic) {
                case STATUS -> status(context);
                case RECOMMENDATION -> recommendation(context, cited);
                case EVIDENCE -> evidence(context, text, cited);
                case MISSING -> missing(context, cited);
                case CITATIONS -> citations(context);
            });
        }
        String content = bounded("Case " + context.caseId() + " (rules mode, advisory only; human review required)\n\n"
                + String.join("\n\n", sections));
        return new CaseAssistantReply(content, List.copyOf(cited), ProcessorMode.RULES);
    }

    private static boolean referencesOtherRecords(CaseMessageContext context) {
        Set<String> known = new HashSet<>(context.evidenceIds());
        known.add(context.caseId());
        FraudCaseRecommendation rec = context.latestRecommendation();
        if (rec != null) {
            known.addAll(rec.traceIds());
        }
        context.evidence().stream().map(EvidenceItem::toolTraceId).filter(Objects::nonNull).forEach(known::add);
        Matcher m = UUID.matcher(context.message());
        while (m.find()) {
            if (!known.contains(m.group().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String status(CaseMessageContext context) {
        String next = CaseStateMachine.allowedTargets(context.status()).stream().map(Enum::name)
                .collect(Collectors.joining(", "));
        return "Status: " + context.status() + ". Allowed next statuses: " + (next.isEmpty() ? "none (closed)" : next)
                + ".";
    }

    private static String recommendation(CaseMessageContext context, Set<String> cited) {
        FraudCaseRecommendation rec = context.latestRecommendation();
        if (rec == null) {
            return "No recommendation has been recorded yet. An analyst can start an investigation for this case.";
        }
        rec.evidenceIds().forEach(cited::add);
        return "Latest recommendation: %s, risk %s, route %s, confidence %s (processor %s).\nRationale: %s"
                .formatted(rec.classification(), rec.riskLevel(), rec.route(), rec.confidence().toPlainString(),
                        rec.processorMode(), rec.rationale());
    }

    private static String evidence(CaseMessageContext context, String text, Set<String> cited) {
        List<EvidenceItem> latest = latestPerTool(context);
        if (latest.isEmpty()) {
            return "No evidence has been collected for this case yet.";
        }
        Set<EvidenceTool> asked = EnumSet.noneOf(EvidenceTool.class);
        TOOL_WORDS.forEach((tool, pattern) -> {
            if (pattern.matcher(text).find()) {
                asked.add(tool);
            }
        });
        List<EvidenceItem> shown = latest.stream().filter(e -> asked.isEmpty() || asked.contains(e.tool())).toList();
        if (shown.isEmpty()) {
            return "No evidence of that kind has been collected for this case.";
        }
        StringBuilder sb = new StringBuilder("Evidence (latest per tool):");
        for (EvidenceItem item : shown) {
            cited.add(item.evidenceId());
            sb.append("\n- ").append(item.tool() == null ? item.provenance().name() : item.tool().toolId())
                    .append(" [").append(item.status()).append("] ").append(truncate(item.summary()))
                    .append(" (evidence ").append(item.evidenceId())
                    .append(", source ").append(item.source() == null ? "n/a" : item.source()).append(')');
        }
        return sb.toString();
    }

    private static String missing(CaseMessageContext context, Set<String> cited) {
        Set<String> lines = new LinkedHashSet<>();
        FraudCaseRecommendation rec = context.latestRecommendation();
        if (rec != null) {
            lines.addAll(rec.missingInformation());
        }
        for (EvidenceItem item : latestPerTool(context)) {
            if (!item.isAvailable()) {
                cited.add(item.evidenceId());
                lines.add((item.tool() == null ? item.provenance().name() : item.tool().toolId())
                        + " returned no evidence (" + item.status() + ")");
            }
        }
        if (lines.isEmpty()) {
            return rec == null ? "Missing information is unknown until the case has been investigated."
                    : "No missing information was recorded for the latest recommendation.";
        }
        return "Missing information:\n- " + String.join("\n- ", lines);
    }

    private static String citations(CaseMessageContext context) {
        FraudCaseRecommendation rec = context.latestRecommendation();
        List<PolicyCitation> citations = rec == null ? List.of() : rec.policyCitations();
        if (citations.isEmpty()) {
            return "No approved policy passage is attached to the latest recommendation, so no procedural guidance "
                    + "can be cited. Refer procedural questions to a human reviewer.";
        }
        return "Policy citations:\n- " + citations.stream()
                .map(c -> "%s v%s, section %s (%s, chunk %s, effective %s)".formatted(c.documentTitle(),
                        c.documentVersion(), c.section(), c.documentId(), c.chunkId(), c.effectiveDate()))
                .collect(Collectors.joining("\n- "));
    }

    private static List<EvidenceItem> latestPerTool(CaseMessageContext context) {
        Map<Object, EvidenceItem> latest = new LinkedHashMap<>();
        for (EvidenceItem item : context.evidence()) {
            Object key = item.tool() == null ? item.provenance() : item.tool();
            latest.remove(key);
            latest.put(key, item);
        }
        return List.copyOf(latest.values());
    }

    private static String truncate(String value) {
        return value.length() <= MAX_SUMMARY_CHARS ? value : value.substring(0, MAX_SUMMARY_CHARS - 1) + "…";
    }

    private static String bounded(String value) {
        int max = CaseMessage.MAX_REPLY_CONTENT_LENGTH;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
