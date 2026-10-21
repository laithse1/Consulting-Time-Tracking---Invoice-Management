package io.consulting.service;

import io.consulting.repository.AuditRepository;
import io.consulting.support.JsonDocuments;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository repository;
    private final JsonDocuments documents;

    public AuditService(AuditRepository repository, JsonDocuments documents) {
        this.repository = repository;
        this.documents = documents;
    }

    public void record(String action, String entity, Object detail) {
        repository.append(action, entity, documents.encode(detail));
    }
}
