package com.example.commons.audit;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Appends and reads audit trail rows for {@link AuditTrail}. It extends
 * {@link Repository} rather than {@code JpaRepository} so that it exposes no update or
 * delete.
 */
public interface AuditTrailEventRepository
		extends Repository<AuditTrailEvent, Long>, JpaSpecificationExecutor<AuditTrailEvent> {

	AuditTrailEvent save(AuditTrailEvent event);

}
