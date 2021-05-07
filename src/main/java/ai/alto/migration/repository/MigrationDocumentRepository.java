package ai.alto.migration.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import ai.alto.migration.domain.MigrationDocument;


public interface MigrationDocumentRepository extends PagingAndSortingRepository<MigrationDocument, String> {
    Page<MigrationDocument> findByIndexNameAndMigratedIsFalse(String indexName, Pageable pageable);

    long countAllByIndexName(String indexName);
}
