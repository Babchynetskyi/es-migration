package ai.alto.migration.service;

import static java.util.stream.Collectors.toList;

import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

@Service
@Slf4j
public class ExportService {
    private static final Integer PAGE = 100;

    @Autowired
    private RestHighLevelClient exportElasticsearchClient;

    @Autowired
    private MigrationDocumentRepository migrationRepository;

    @Value("${env}")
    private String exportEnv;

    @Autowired
    private MigrationIndexRepository migrationIndexRepository;

    private RequestOptions options;

    @PostConstruct
    public void init() {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setSocketTimeout(120000)
            .setConnectionRequestTimeout(120000)
            .build();
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder()
            .setRequestConfig(requestConfig);
        builder.setHttpAsyncResponseConsumerFactory(new HttpAsyncResponseConsumerFactory
            .HeapBufferedResponseConsumerFactory(500 * 1024 * 1024));
        options = builder.build();
    }

    @Async("threadPoolTaskExecutor")
    public void exportIndex(String indexName) {
        try {
            if (!exportElasticsearchClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
                exportElasticsearchClient.indices().create(new CreateIndexRequest(indexName), RequestOptions.DEFAULT);
            }
            Page<MigrationDocument> documents;
            Pageable pageable = PageRequest.of(0, PAGE);
            long total = 0;
            do {
                documents = migrationRepository.findByIndexNameAndMigratedIsFalse(indexName, pageable);

                if (!documents.isEmpty()) {
                    BulkRequest bulkRequest = new BulkRequest();
                    documents.forEach(req -> updateBulkRequest(bulkRequest, req, indexName));
                    BulkResponse bulkResponse = exportElasticsearchClient.bulk(bulkRequest, options);
                    documents.forEach(doc -> doc.setMigrated(true));
                    migrationRepository.saveAll(documents);
                    total += documents.getSize();
                    log.info("{} documents of index {} are exported", total, indexName);
                }

            } while (documents.getNumberOfElements() == PAGE);
            log.info("Index {} is exported", indexName);
        } catch (Exception exc) {
            log.error("Index {} export is failed with error {}", indexName, exc.getMessage());
        }
    }

    public List<String> getIndices() {
        return migrationIndexRepository.findByEnv(exportEnv)
            .stream()
            .map(MigrationIndex::getIndexName)
            .collect(toList());
    }

    public Map<String, String> validateImport() throws IOException {
        List<MigrationIndex> indexes = migrationIndexRepository.findByEnv(exportEnv);

        Map<String, String> errors = new HashMap<>();
        for (MigrationIndex index : indexes) {

            long elasticCount = getIndexCount(index.getIndexName());

            long dbCount = migrationRepository.countAllByIndexName(index.getIndexName());
            if (dbCount != elasticCount) {
                log.error("Index {} documents count mismatch: elastic {}, db {}", index.getIndexName(), elasticCount, dbCount);
                errors.put(index.getIndexName(), elasticCount + ":" + dbCount);
            }
        }
        return errors;
    }

    public long getIndexCount(String index) {
        try {
            return exportElasticsearchClient.count(new CountRequest(index), options).getCount();
        } catch (IOException io) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void updateBulkRequest(BulkRequest bulkRequest, MigrationDocument migratedDocument, String index) {
        Map<String, String> source = new HashMap<>();
        source.put("data", migratedDocument.getData());
        source.put("reference", migratedDocument.getReference());
        bulkRequest.add(new IndexRequest(index)
            .id(migratedDocument.getId())
            .type("documents")
            .source(source, XContentType.JSON));
    }


}
