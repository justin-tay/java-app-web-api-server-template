package com.example.commons.accounts.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Reads and appends audit events. It extends {@link Repository} rather than
 * {@code JpaRepository} so that it exposes no update or delete.
 */
public interface AccountAuditEventRepository
		extends Repository<AccountAuditEvent, Long>, JpaSpecificationExecutor<AccountAuditEvent> {

	AccountAuditEvent save(AccountAuditEvent event);

	List<AccountAuditEvent> findAllById(Iterable<Long> ids);

	/**
	 * Returns the events of one action on the given targets, such as the suspensions of a
	 * set of accounts.
	 */
	List<AccountAuditEvent> findByActionAndTargetIdIn(String action, Collection<String> targetIds);

}
