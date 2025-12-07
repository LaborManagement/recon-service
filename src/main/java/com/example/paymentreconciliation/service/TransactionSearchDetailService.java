package com.example.paymentreconciliation.service;

import org.slf4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.paymentreconciliation.dao.TransactionSearchDetailDao;
import com.example.paymentreconciliation.model.TransactionSearchDetailSearchRequest;
import com.example.paymentreconciliation.model.TransactionSearchDetailView;
import com.shared.common.dao.TenantAccessDao;
import com.shared.common.dto.SecurePaginationResponse;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Service
@Transactional(readOnly = true)
public class TransactionSearchDetailService {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionSearchDetailService.class);
    private static final java.util.List<String> ALLOWED_SORT_FIELDS = java.util.List.of(
            "receiptDate",
            "createdAt",
            "amount",
            "id");

    private final TransactionSearchDetailDao dao;
    private final TenantAccessDao tenantAccessDao;

    public TransactionSearchDetailService(TransactionSearchDetailDao dao, TenantAccessDao tenantAccessDao) {
        this.dao = dao;
        this.tenantAccessDao = tenantAccessDao;
    }

    public SecurePaginationResponse<TransactionSearchDetailView> search(
            TransactionSearchDetailSearchRequest request,
            SecurePaginationUtil.ValidationResult validation) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (validation == null || !validation.isValid()) {
            throw new IllegalArgumentException("Invalid pagination request");
        }

        TenantAccessDao.TenantAccess ta = tenantAccessDao.getFirstAccessibleTenant();
        if (ta == null || ta.boardId == null || ta.employerId == null) {
            throw new IllegalStateException("User has no tenant access (board/employer/toli) for search");
        }

        Sort sort = SecurePaginationUtil.createSecureSort(request, ALLOWED_SORT_FIELDS);
        int page = Math.max(request.getPage(), 0);
        int size = Math.max(1, Math.min(request.getSize(), 100));
        var pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);

        log.info(
                "Searching transaction_search_details startDate={}, endDate={}, txnType={}, txnRef={}, status={}, uploadId={}, mapped={}, page={}, size={}, sortBy={}, sortDir={}",
                validation.getStartDateTime(),
                validation.getEndDateTime(),
                request.getTxnRef(),
                request.getUploadId(),
                page,
                size,
                request.getSortBy(),
                request.getSortDir());

        var pageResult = dao.searchPaginated(
                request,
                ta,
                pageable,
                validation.getStartDateTime().toLocalDate(),
                validation.getEndDateTime().toLocalDate());
        return SecurePaginationUtil.createSecureResponse(pageResult, request);
    }
}
