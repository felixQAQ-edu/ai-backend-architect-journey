package com.felix.chatpipeline.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 团队知识库 Tool 集合（M1 mock 版本）。
 * <p>
 * 设计要点：
 * - 每个方法上的 @Tool 描述会被自动转成 Function Schema 的 description，喂给 LLM。LLM 读这段描述决定何时调用
 * - @P 参数描述同样进入 Schema，参数名也会带过去（编译时加 -parameters 才能保留 Java 参数名，Spring Boot 默认开启）
 * - 参数校验失败抛 IllegalArgumentException，LangChain4j 会把异常 message 作为 tool result 回传给 LLM，
 *   LLM 通常能自己看着错误重试 —— 这是 Agent 自纠错的关键机制。M1 阶段务必体验一次"提故意非法参数 → LLM 自己改对"的流程
 * - 当前数据是内存 Map mock；M2 阶段把方法体替换成真实 API 调用即可（业务签名不变 = ADR-004 的核心理由 1 兑现）
 */
@Component
public class KnowledgeBaseTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTools.class);

    /** Mock 文档库 */
    private final Map<String, Document> documents = new LinkedHashMap<>();

    public KnowledgeBaseTools() {
        seed("doc-001", "工程", "微服务架构指南",
                "本指南阐述团队微服务拆分原则：单一职责、数据独立、API 网关统一入口。建议初期不超过 5 个服务。");
        seed("doc-002", "工程", "代码评审规范",
                "PR 必须包含：变更说明、测试覆盖、影响面分析。评审周期 24 小时内响应。");
        seed("doc-003", "工程", "Git 分支策略",
                "采用 trunk-based development，feature 分支生命周期不超过 3 天。");
        seed("doc-004", "产品", "Q1 路线图",
                "Q1 重点：用户增长 30%、核心链路稳定性 99.9%、推出付费订阅。");
        seed("doc-005", "产品", "用户调研访谈纪要",
                "10 位用户访谈结论：核心痛点是数据导出格式有限，定价被认为偏高。");
        seed("doc-006", "运营", "客服 SOP",
                "客服响应时长目标 P50 < 5 分钟。退款规则：30 天内无条件退款。");
    }

    // ==================== Tool 1: 关键词搜索 ====================

    @Tool("根据关键词搜索团队文档，返回标题和摘要。模糊问题先用这个工具拿候选。")
    public List<DocumentSummary> searchDocuments(
            @P("搜索关键词，必填") String query,
            @P("返回的最大结果数，必须在 1-20 之间") int limit
    ) {
        // ---- 参数校验：失败抛异常，LLM 收到 error 后通常会自己改正参数重试 ----
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit 必须在 1-20 之间，当前 = " + limit);
        }

        log.info("[Tool] searchDocuments query='{}' limit={}", query, limit);

        String q = query.toLowerCase();
        return documents.values().stream()
                .filter(d -> d.title.toLowerCase().contains(q) || d.content.toLowerCase().contains(q))
                .limit(limit)
                .map(d -> new DocumentSummary(d.id, d.category, d.title,
                        d.content.length() > 60 ? d.content.substring(0, 60) + "..." : d.content))
                .toList();
    }

    // ==================== Tool 2: 全文获取 ====================

    @Tool("根据 documentId 获取文档完整内容")
    public Document getDocumentById(
            @P("文档 ID，形如 doc-001") String documentId
    ) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        log.info("[Tool] getDocumentById id={}", documentId);

        Document doc = documents.get(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("找不到文档: " + documentId);
        }
        return doc;
    }

    // ==================== Tool 3: 分类浏览 ====================

    @Tool("列出指定分类下的所有文档。可用分类：工程、产品、运营")
    public List<DocumentSummary> listDocumentsByCategory(
            @P("文档分类，仅支持：工程、产品、运营") String category
    ) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category 不能为空");
        }
        Set<String> validCategories = Set.of("工程", "产品", "运营");
        if (!validCategories.contains(category)) {
            throw new IllegalArgumentException("无效分类: " + category + "，可用值: " + validCategories);
        }
        log.info("[Tool] listDocumentsByCategory category={}", category);

        return documents.values().stream()
                .filter(d -> d.category.equals(category))
                .map(d -> new DocumentSummary(d.id, d.category, d.title, ""))
                .toList();
    }

    // ==================== Tool 4: 当前时间 ====================

    @Tool("获取当前日期与时间。涉及「今天」「最近」等时间相关问题时调用")
    public String getCurrentDateTime() {
        log.info("[Tool] getCurrentDateTime");
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // ==================== Tool 5: 文档摘要 ====================

    @Tool("对指定文档生成简短摘要")
    public String summarizeDocument(
            @P("文档 ID") String documentId,
            @P("摘要句子数，1-5 之间") int maxSentences
    ) {
        if (maxSentences < 1 || maxSentences > 5) {
            throw new IllegalArgumentException("maxSentences 必须在 1-5 之间");
        }
        Document doc = getDocumentById(documentId);  // 复用上面的校验
        log.info("[Tool] summarizeDocument id={} maxSentences={}", documentId, maxSentences);

        // M1 mock：按句号简单切分。M6-M8 RAG 阶段会换成真正的语义摘要
        String[] sentences = doc.content.split("[。！？]");
        return String.join("。",
                Arrays.stream(sentences).limit(maxSentences).toList()) + "。";
    }

    // ==================== 内部类型 ====================

    private void seed(String id, String category, String title, String content) {
        documents.put(id, new Document(id, category, title, content));
    }

    /** 文档完整结构 */
    public record Document(String id, String category, String title, String content) {}

    /** 文档摘要（搜索结果用） */
    public record DocumentSummary(String id, String category, String title, String snippet) {}
}
