package com.example.commons.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes and reads the audit trail rows: in the caller's transaction, in a transaction of
 * their own that the caller's rollback cannot undo, and through the two kinds of query.
 */
final class AuditRows {

	private final AuditTrailEventRepository events;

	private final TransactionTemplate newTransaction;

	AuditRows(AuditTrailEventRepository events, PlatformTransactionManager transactionManager) {
		this.events = events;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Saves a row in the caller's transaction, or in one of its own if there is none.
	 */
	AuditTrailEvent save(AuditTrailEvent row) {
		return this.events.save(row);
	}

	/**
	 * Saves a row in a new transaction, committed before this returns.
	 */
	void saveInNewTransaction(AuditTrailEvent row) {
		this.newTransaction.executeWithoutResult(status -> this.events.save(row));
	}

	List<AuditTrailEvent> find(AuditQuery query) {
		Specification<AuditTrailEvent> specification = Specification.unrestricted();
		if (!query.isAnyOutcome()) {
			specification = specification.and(equal("outcome", AuditOutcome.SUCCESS));
		}
		if (query.actions() != null) {
			specification = specification.and(in("action", query.actions()));
		}
		if (query.targetType() != null) {
			specification = specification.and(equal("targetType", query.targetType()));
		}
		if (query.targetIds() != null) {
			specification = specification.and(in("targetId", query.targetIds()));
		}
		if (query.ids() != null) {
			specification = specification.and(in("publicId", query.ids()));
		}
		if (query.since() != null) {
			specification = specification
				.and((root, criteria, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), query.since()));
		}
		return this.events.findAll(specification, Sort.by("occurredAt"));
	}

	Page<AuditTrailEvent> search(AuditSearch search, Pageable pageable) {
		Specification<AuditTrailEvent> specification = Specification.unrestricted();
		specification = specification.and(contains("actor", search.actor()));
		specification = specification.and(contains("targetName", search.targetName()));
		specification = specification.and(contains("action", search.action()));
		if (search.targetType() != null) {
			specification = specification.and(equal("targetType", search.targetType()));
		}
		if (search.outcome() != null) {
			specification = specification.and(equal("outcome", search.outcome()));
		}
		if (search.from() != null) {
			specification = specification
				.and((root, criteria, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), search.from()));
		}
		if (search.to() != null) {
			specification = specification
				.and((root, criteria, builder) -> builder.lessThan(root.get("occurredAt"), search.to()));
		}
		return this.events.findAll(specification, pageable);
	}

	private static Specification<AuditTrailEvent> equal(String field, Object value) {
		return (root, criteria, builder) -> builder.equal(root.get(field), value);
	}

	private static Specification<AuditTrailEvent> in(String field, List<?> values) {
		return (root, criteria, builder) -> values.isEmpty() ? builder.disjunction() : root.get(field).in(values);
	}

	private static Specification<AuditTrailEvent> contains(String field, String value) {
		if (value == null || value.isBlank()) {
			return Specification.unrestricted();
		}
		String pattern = "%" + value.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return (root, criteria, builder) -> builder.like(builder.lower(root.get(field)), pattern, '\\');
	}

}
