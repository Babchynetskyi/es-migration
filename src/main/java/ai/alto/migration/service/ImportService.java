package ai.alto.migration.service;

import static java.util.stream.Collectors.toList;

import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ai.alto.migration.domain.MigrationDocument;
import ai.alto.migration.domain.MigrationIndex;
import ai.alto.migration.repository.MigrationDocumentRepository;
import ai.alto.migration.repository.MigrationIndexRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

@Service
@Slf4j
public class ImportService {
    private static final Integer PAGE = 50;

    @Autowired
    private RestHighLevelClient importElasticsearchClient;

    @Autowired
    private MigrationDocumentRepository migrationRepository;

    @Autowired
    private MigrationIndexRepository migrationIndexRepository;

    private RequestOptions options;

    @Value("${env}")
    private String env;

    @PostConstruct
    public void init() {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setSocketTimeout(600000)

            .build();
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder()
            .setRequestConfig(requestConfig);
        builder.setHttpAsyncResponseConsumerFactory(new HttpAsyncResponseConsumerFactory
            .HeapBufferedResponseConsumerFactory(500 * 1024 * 1024));
        options = builder.build();
    }

    @Async("threadPoolTaskExecutor")
    public void importIndex(MigrationIndex index) {
        try {
            int from = 0;
            SearchResponse res;
            String scrollId = null;
            do {

                if (scrollId == null) {
                    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                    searchSourceBuilder.size(PAGE);

                    SearchRequest getRequest = new SearchRequest(new String[]{index.getIndexName()}, searchSourceBuilder);
                    getRequest.scroll(new TimeValue( 5 * 1000));
                    res = importElasticsearchClient.search(getRequest, options);
                } else {
                    res = importElasticsearchClient.scroll(new SearchScrollRequest(scrollId).scroll(new TimeValue(5 * 1000)), options);
                }
                log.info("Fetched hits {} from index {}. Total hits {}", from + res.getHits().getHits().length, index, res.getHits().getTotalHits().value);
                for (SearchHit hit : res.getHits()) {
                    MigrationDocument document = new MigrationDocument();
                    document.setIndexName(index.getIndexName());
                    document.setId(hit.getId());
                    document.setData((String) hit.getSourceAsMap().get("data"));
                    document.setReference((String) hit.getSourceAsMap().get("reference"));
                    migrationRepository.save(document);
                }
                scrollId = res.getScrollId();
                from += PAGE;
            } while (res.getHits().getHits().length == PAGE);
        } catch (Exception ioException) {
            log.error("Error for index {} {}", index, ioException.getMessage());
            return;
        }

        migrationIndexRepository.save(index);

        log.info("Index is processed {}", index);
    }

    @Async("threadPoolTaskExecutor")
    public void importIndex(String indexName) {
        importIndex(new MigrationIndex(indexName, env, getIndexCount(indexName)));
    }

    public List<MigrationIndex> getIndices() throws IOException {
        GetIndexResponse indexes = importElasticsearchClient.indices().get(new GetIndexRequest("*"), options);

        return Arrays.stream(indexes.getIndices())
            .filter(ind -> !ind.startsWith("."))
            .filter(ind -> migrationIndexRepository.findById(ind).isEmpty())
            .map(ind -> new MigrationIndex(ind, env, getIndexCount(ind)))
            .sorted(Comparator.comparing(MigrationIndex::getTotalHits).reversed())
            .collect(toList());
    }

    public Map<String, String> validateImport() throws IOException {
        GetIndexResponse indexes = importElasticsearchClient.indices().get(new GetIndexRequest("*"), options);

        Map<String, String> errors = new HashMap<>();
        for (String indexName : indexes.getIndices()) {
            MigrationIndex index = migrationIndexRepository.findById(indexName)
                .orElse(null);
            if (index == null) {
                errors.put(indexName, "Migration Index record is missing");
            }
            long elasticCount = getIndexCount(indexName);

            long dbCount = migrationRepository.countAllByIndexName(indexName);
            if (dbCount != elasticCount) {
                log.error("Index {} documents count mismatch: elastic {}, db {}", indexName, elasticCount, dbCount);
                errors.put(indexName, elasticCount + ":" + dbCount);
            }


        }
        return errors;
    }

    public long getIndexCount(String index) {
        try {
            return importElasticsearchClient.count(new CountRequest(index), options).getCount();
        } catch (IOException io) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
