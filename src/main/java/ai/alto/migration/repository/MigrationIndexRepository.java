package ai.alto.migration.repository;

import org.springframework.data.repository.PagingAndSortingRepository;

import ai.alto.migration.domain.MigrationIndex;

import java.util.List;

public interface MigrationIndexRepository extends PagingAndSortingRepository<MigrationIndex, String> {
    List<MigrationIndex> findByEnv(String env);
}
