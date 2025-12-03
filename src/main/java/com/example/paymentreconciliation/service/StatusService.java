package com.example.paymentreconciliation.service;

import org.springframework.stereotype.Service;

import com.shared.common.util.StatusResolver;

@Service
public class StatusService {

    private final StatusResolver statusResolver;

    public StatusService(StatusResolver statusResolver) {
        this.statusResolver = statusResolver;
    }

    public String resolveStatusCode(String statusType, Integer statusId) {
        if (statusId == null) {
            return null;
        }
        return statusResolver.code(statusType, statusId);
    }

    public Integer requireStatusId(String statusType, String statusCode) {
        Integer id = statusResolver.seqNo(statusType, statusCode);
        if (id == null) {
            throw new IllegalArgumentException("Unknown status code: " + statusCode);
        }
        return id;
    }
}
