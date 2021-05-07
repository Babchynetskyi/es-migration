package ai.alto.migration.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.alto.migration.domain.MigrationIndex;
import ai.alto.migration.service.ExportService;
import ai.alto.migration.service.ImportService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/migrate", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class MigrationController {
    @Autowired
    private ImportService importService;

    @Autowired
    private ExportService exportService;

    @PostMapping(path = "/import")
    public void importIndices() throws IOException {
        List<MigrationIndex> indices = importService.getIndices();
        log.info("Fetched {} indices for import", indices.size());
        indices.forEach(importService::importIndex);
    }

    @PostMapping(path = "/import/{index}")
    public void importIndex(@PathVariable String index) {
        importService.importIndex(index);
    }

    @PostMapping(path = "/import/validate")
    public Map<String, String> validateImport() throws IOException {
        return  importService.validateImport();
    }

    @PostMapping(path = "/export")
    public void exportIndices() {
        List<String> indices = exportService.getIndices();
        log.info("Fetched {} indices for export", indices.size());
        indices.forEach(exportService::exportIndex);
    }

    @PostMapping(path = "/export/{index}")
    public void exportIndex(@PathVariable String index) {
        exportService.exportIndex(index);
    }

    @PostMapping(path = "/export/validate")
    public Map<String, String> validateExport() throws IOException {
        return  exportService.validateImport();
    }

}
