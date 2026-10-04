package com.example.commons.accounts.domain;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Reads and appends audit events. It extends {@link Repository} rather than
 * {@code JpaRepository} so that it exposes no update or delete.
 */
public interface AccountAuditEventRepository
		extends Repository<AccountAuditEvent, Long>, JpaSpecificationExecutor<AccountAuditEvent> {

	AccountAuditEvent save(AccountAuditEvent event);

}
