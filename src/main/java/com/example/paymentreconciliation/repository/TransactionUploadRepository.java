package com.example.paymentreconciliation.repository;

import com.example.paymentreconciliation.entity.TransactionUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionUploadRepository extends JpaRepository<TransactionUpload, Long> {
    Optional<TransactionUpload> findByFileHash(String fileHash);
}
