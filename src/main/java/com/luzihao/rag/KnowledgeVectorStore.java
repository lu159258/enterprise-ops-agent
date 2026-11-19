package com.luzihao.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.luzihao.infra.entity.KnowledgeDocument;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Component
@ConditionalOnProperty(name = "app.rag.mode",havingValue = "vector")
@Slf4j
public class KnowledgeVectorStore {


    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;

    @Value("${app.rag.min-score}")
    private double minScore;
    @Value("${app.rag.chunk-overlap}")
    private int chunkOverLap;
    @Value("${app.rag.chunk-size}")
    private int chunkSize;


    public KnowledgeVectorStore(EmbeddingModel embeddingModel,
                                @Value("${app.es.url}") String url,
                                @Value("${app.es.username:}") String username,
                                @Value("${app.es.password:}") String password,
                                @Value("${app.es.index:knowledge-chunks}") String index,
                                @Value("${app.es.dimension:1536}") int dimension) throws NoSuchAlgorithmException, KeyManagementException {
        this.embeddingModel =  embeddingModel;

        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        // 2. 信任所有证书（仅本地开发）
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, null);

        // 3. 构建 RestClient
        URI uri = URI.create(url);

        RestClient restClient = RestClient.builder(
              new  HttpHost(uri.getHost(),uri.getPort(),uri.getScheme())
        ).setHttpClientConfigCallback(hc -> hc
                .setDefaultCredentialsProvider(creds)
                .setSSLContext(sslContext)
        ).build();

        // 4. 构建 ElasticsearchClient
        ElasticsearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchEmbeddingStore.Builder builder = ElasticsearchEmbeddingStore.builder().client(new ElasticsearchClient(transport)).indexName(index);

        this.store  = builder.build();
    }


    public void ingest(KnowledgeDocument doc){
        Map<String,String> map = new HashMap<>();
        map.put("documentId", doc.getId());
        map.put("tenantId", doc.getTenantId());
        map.put("title", doc.getTitle());
        TextSegment whole = TextSegment.from(doc.getContent(), Metadata.from(map));
        List<TextSegment> chunks = DocumentSplitters.recursive(chunkSize, chunkOverLap).split(
                Document.document(whole.text(), whole.metadata())
        );

       /* for (TextSegment chunk : chunks) {
            TextSegment withMeta = TextSegment.from(chunk.text(), whole.metadata());
            Embedding emb = embeddingModel.embed(withMeta.text()).content();
            store.add(emb,withMeta);
        }*/

        List<TextSegment> withMeta = chunks.stream().map(c -> TextSegment.from(c.text(), whole.metadata())).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(withMeta).content();
        store.addAll(embeddings,withMeta);

        log.info("向量入库完成 docId={} chunks={}", doc.getId(), chunks.size());

    }

    public List<KnowledgeHit> search(String tenantId,String query,int topK){
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding).maxResults(topK)
                .minScore(minScore)
                .filter(metadataKey("tenantId").isEqualTo(tenantId))
                .build()
        );

        return result.matches().stream().map(this::toHit).toList();

    }

    private KnowledgeHit toHit(EmbeddingMatch<TextSegment> match){
        TextSegment embedded = match.embedded();
        String docId = embedded.metadata().getString("documentId");
        String text = embedded.text();
        return new KnowledgeHit("doc-" + docId,docId,embedded.metadata().getString("title"),
                text,
                null,
                "PUBLIC",
                match.score()

                );
    }
    /** 按 docId 清理 ES 向量 */
    public void removeByDocId(String docId){
        store.removeAll(metadataKey("documentId").isEqualTo(docId));
    }

}
