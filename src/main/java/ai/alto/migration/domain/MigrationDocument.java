package ai.alto.migration.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "migration_document")
@Data
public class MigrationDocument {
    @Id
    @Column(name = "id")
    private String id;
    @Column(name = "index_name")
    private String indexName;
    @Column(name = "data")
    private String data;
    @Column(name = "reference")
    private String reference;
    @Column
    private boolean migrated;
}