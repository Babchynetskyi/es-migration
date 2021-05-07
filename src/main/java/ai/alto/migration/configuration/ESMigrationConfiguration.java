package ai.alto.migration.configuration;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ESMigrationConfiguration {
    @Value("${import.es.host}")
    private String importEsHost;
    @Value("${export.es.host}")
    private String exportEsHost;

    @Bean
    public RestHighLevelClient importElasticsearchClient() {
        return new RestHighLevelClient(RestClient.builder(HttpHost.create(importEsHost)));
    }

    @Bean
    public RestHighLevelClient exportElasticsearchClient() {
        return new RestHighLevelClient(RestClient.builder(HttpHost.create(exportEsHost)));
    }

    @Bean(name = "threadPoolTaskExecutor")
    public Executor threadPoolTaskExecutor() {
        return Executors.newFixedThreadPool(5);
    }
}
