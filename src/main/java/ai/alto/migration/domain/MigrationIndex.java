package ai.alto.migration.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "migration_index")
@Data
public class MigrationIndex {

    @Id
    @Column(name = "index_name")
    private String indexName;

    @Column(name = "env")
    private String env;

    @Column(name = "total_hits")
    private long totalHits;

    public MigrationIndex() {
    }

    public MigrationIndex(String indexName, String env, long totalHits) {
        this.indexName = indexName;
        this.totalHits = totalHits;
        this.env = env;
    }
}
