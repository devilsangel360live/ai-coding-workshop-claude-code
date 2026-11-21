package com.example.taskmanager.infrastructure.legacy;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LegacyBatchImportService {

    private final LegacyTaskProcessor processor;

    public LegacyBatchImportService(LegacyTaskProcessor processor) {
        this.processor = processor;
    }

    public record ImportRecord(int id, String data, int type) {}

    public List<String> importBatch(List<ImportRecord> records) {
        processor.init(true);
        var results = new ArrayList<String>();

        for (var record : records) {
            if (processor.canProcess(record.data(), record.type())) {
                var result = processor.processTask(record.id(), record.data(), record.type(), true);
                results.add(result);
            }
        }

        return results;
    }

    public int getProcessedCount() { return processor.getProcessedCount(); }
    public String getLastProcessed() { return processor.getLastResult(); }
}
