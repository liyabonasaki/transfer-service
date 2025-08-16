package com.sun.transfer_service.repository;

import com.sun.transfer_service.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByTransferId(String transferId);
}
