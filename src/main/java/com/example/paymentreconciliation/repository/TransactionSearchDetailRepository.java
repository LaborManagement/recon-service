package com.example.paymentreconciliation.repository;

import com.example.paymentreconciliation.entity.TransactionSearchDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionSearchDetailRepository extends JpaRepository<TransactionSearchDetail, Long> {
    List<TransactionSearchDetail> findByUploadId(Long uploadId);
}
