package com.felix.chatpipeline.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingLogRepository extends JpaRepository<BillingLog, Long> {

    Optional<BillingLog> findByRequestId(String requestId);
}