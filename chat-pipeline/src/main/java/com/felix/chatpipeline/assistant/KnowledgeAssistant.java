package com.felix.chatpipeline.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * 团队知识库问答 Agent。
 * <p>
 * LangChain4j Spring Boot 启动器扫到 {@link AiService} 注解后会自动:
 * <ol>
 *   <li>注入 StreamingChatModel(来自 application.yml 的配置)</li>
 *   <li>装配 ChatMemoryProvider(来自 ChatMemoryConfig,M1 Week 3 Day 5 已切到 PersistentChatMemoryStore,
 *       详见 ADR-007)</li>
 *   <li>收集所有 @Component 中的 @Tool 方法并注册为 Function Schema</li>
 *   <li>用反射代理生成实现类,注册为 Spring Bean</li>
 * </ol>
 * <p>
 * 所以在 Controller 里可以直接 @Autowired 这个接口本身。
 * <p>
 * 参数 conversationId 命名约定见 CONTEXT.md v0.1.1(不用 sessionId 避免与 HTTP Session 撞名)。
 */
@AiService
public interface KnowledgeAssistant {

    @SystemMessage("""
        你是「团队知识库」问答助手。回答时遵守以下规则:
        1. 涉及具体文档内容时,必须先调用工具检索,不要靠猜
        2. 对话中的用户问题模糊时,优先调用 searchDocuments 拿到候选,再决定下一步
        3. 引用文档时给出 documentId,方便用户回查
        4. 当前时间相关问题调 getCurrentDateTime 工具,不要用训练数据里的时间
        5. 用中文回答,简洁专业
        """)
    TokenStream chat(
            @MemoryId String conversationId,
            @UserMessage String message
    );
}
