package com.luzihao.core.ai;

import com.luzihao.tools.OpsTools;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class AiConfig {
    @Autowired
    private PgChatMemoryStore pgChatMemoryStore;

    @Bean
    public ChatModel chatModel(@Value("${app.ai.api-key}") String apiKey,
                               @Value("${app.ai.base-url}") String baseUrl,
                               @Value("${app.ai.model-name}") String modelName,
                               @Value("${app.ai.temperature:0.1}") double temperature,
                               @Value("${app.ai.timeout-seconds:60}") long timeoutSeconds){
        if (!StringUtils.hasText(apiKey)){
            throw new IllegalStateException("未配置 LLM_API_KEY 环境变量");

        }

        return QwenChatModel.builder().apiKey(apiKey).
                baseUrl(baseUrl)
                .modelName(modelName).isMultimodalModel(true)
                //.temperature(temperature)
               // .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(@Value("${app.ai.api-key}") String apiKey,
                                                 @Value("${app.ai.base-url}") String baseUrl,
                                                 @Value("${app.ai.model-name}") String modelName,
                                                 @Value("${app.ai.temperature:0.1}") Float temperature,
                                                 @Value("${app.ai.timeout-seconds:60}") long timeoutSeconds){
        return QwenStreamingChatModel.builder().apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName).isMultimodalModel(true).temperature(temperature).build();
    }
    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        return memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(20).chatMemoryStore(pgChatMemoryStore). build();
    }

    @Bean
    public OpsAssistant opsAssistant(ChatModel chatModel, ChatMemoryProvider chatMemoryProvider, OpsTools opsTools,StreamingChatModel streamingChatModel){
        return AiServices.builder(OpsAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .streamingChatModel(streamingChatModel)
                .tools(opsTools)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(@Value("${app.ai.api-key}") String apiKey,
                                         @Value("${app.ai.base-url}") String baseUrl,
                                         @Value("${app.rag.dimension}") Integer dimension,
                                         @Value("${app.rag.embedding-model-name}") String modelName){

        QwenEmbeddingModel qwenEmbeddingModel = new QwenEmbeddingModel(baseUrl, apiKey, modelName, dimension);
        return qwenEmbeddingModel;

    }
    @Bean
    public DiagnoseAssistant diagnoseAssistant(ChatModel chatModel){
        return AiServices.builder(DiagnoseAssistant.class).chatModel(chatModel).build();
    }
}
