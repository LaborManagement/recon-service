package com.example.paymentreconciliation.repository;

import com.example.paymentreconciliation.entity.ManualTransactionUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ManualTransactionUploadRepository extends JpaRepository<ManualTransactionUpload, Long> {
    Optional<ManualTransactionUpload> findByTxnRefAndTxnDateAndTxnAmount(String txnRef,
            java.time.LocalDate txnDate, java.math.BigDecimal txnAmount);
    List<ManualTransactionUpload> findByImportRunIdOrderByIdDesc(Long importRunId);
}
