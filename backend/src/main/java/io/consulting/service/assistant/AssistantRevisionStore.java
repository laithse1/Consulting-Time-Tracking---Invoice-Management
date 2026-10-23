package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.require;

import io.consulting.repository.AssistantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits a completed language request with a version check, outside the network call's scope. */
@Service
public class AssistantRevisionStore {

    private final AssistantRepository repository;

    public AssistantRevisionStore(AssistantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(
        String id,
        int version,
        String instruction,
        String engine,
        String reply,
        String before,
        String after,
        int limit
    ) {
        require(
            repository.findRevisions(id).size() < limit,
            "Conversation limit reached. Use the review fields or start a new plan."
        );
        require(
            repository.updateProposedPlan(id, version, after),
            "Plan changed while processing your correction. Reload the saved plan and try again."
        );
        repository.saveRevision(id, version + 1, instruction, engine, reply, before, after);
    }
}
