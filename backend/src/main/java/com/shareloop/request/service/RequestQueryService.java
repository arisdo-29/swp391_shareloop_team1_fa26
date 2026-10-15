package com.shareloop.request.service;

import com.shareloop.request.repository.RequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only request rules used by other modules without depending on RequestService. */
@Service
public class RequestQueryService {
    private final RequestRepository repository;

    public RequestQueryService(RequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean hasReservedOrHigherRequest(long itemId) {
        return repository.hasReservedOrHigherRequest(itemId);
    }
}
