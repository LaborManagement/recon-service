package com.example.paymentreconciliation.service;

import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.paymentreconciliation.dao.TransactionSearchDetailDao;
import com.example.paymentreconciliation.model.TransactionSearchDetailSearchRequest;
import com.example.paymentreconciliation.model.TransactionSearchDetailView;
import com.shared.common.dao.TenantAccessDao;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Service
@Transactional(readOnly = true)
public class TransactionSearchDetailService {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionSearchDetailService.class);
    private final TransactionSearchDetailDao dao;
    private final TenantAccessDao tenantAccessDao;

    public TransactionSearchDetailService(TransactionSearchDetailDao dao, TenantAccessDao tenantAccessDao) {
        this.dao = dao;
        this.tenantAccessDao = tenantAccessDao;
    }

    public java.util.List<TransactionSearchDetailView> search(
            TransactionSearchDetailSearchRequest request,
            SecurePaginationUtil.ValidationResult validation) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (validation == null || !validation.isValid()) {
            throw new IllegalArgumentException("Invalid request");
        }

        TenantAccessDao.TenantAccess ta = tenantAccessDao.getFirstAccessibleTenant();
        if (ta == null || ta.boardId == null || ta.employerId == null) {
            throw new IllegalStateException("User has no tenant access (board/employer/toli) for search");
        }

        log.info(
                "Searching transaction_search_details summary startDate={}, endDate={}, requestNmbr={}, status={}, uploadId={}",
                validation.getStartDateTime(),
                validation.getEndDateTime(),
                request.getRequestNmbr(),
                request.getStatus(),
                request.getUploadId());

        return dao.searchSummary(
                request,
                ta,
                validation.getStartDateTime().toLocalDate(),
                validation.getEndDateTime().toLocalDate());
    }
}
