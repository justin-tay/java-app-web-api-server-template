package com.example.commons.audit;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The audit trail (see docs/adr/0040): every audited event is a row in
 * {@code audit_event}, which the application shows, and an ECS log event, which is the
 * copy a breached application cannot rewrite. The two carry the same public ID.
 *
 * <ul>
 * <li>{@link #record(AuditEvent)} records a change that was made. The row is written in
 * the caller's transaction and the log event after it commits, so a change that is rolled
 * back leaves neither.</li>
 * <li>{@link #reject(AuditEvent, Supplier)} records a change a business rule refused,
 * under the action it attempted with the outcome {@code failure}. The log event is
 * written at once and the row in a transaction of its own, so it survives the rollback of
 * the caller's, and the caller throws the exception it returns.</li>
 * <li>{@link #find(AuditQuery)} and {@link #search(AuditSearch, Pageable)} read the trail
 * back.</li>
 * </ul>
 *
 * <p>
 * The actor is the authenticated user, or {@code system} (see {@link Auditor}), and the
 * time comes from the clock; a caller sets neither.
 */
public class AuditTrail {

	private final AuditRows rows;

	private final Clock clock;

	public AuditTrail(AuditTrailEventRepository events, PlatformTransactionManager transactionManager, Clock clock) {
		this.rows = new AuditRows(events, transactionManager);
		this.clock = clock;
	}

	/**
	 * Records a change that was made.
	 * @param event the event
	 * @return the recorded event
	 */
	public AuditRecord record(AuditEvent event) {
		AuditTrailEvent row = this.rows.save(row(event, AuditOutcome.SUCCESS));
		afterCommit(() -> AuditLog.write(event, row));
		return new AuditRecord(row);
	}

	/**
	 * Records a change that a business rule refused, and returns the exception for the
	 * caller to throw. If the row cannot be written, the failure is logged and the
	 * exception is still returned, because nothing changed and the log event is already
	 * written.
	 * @param <X> the exception type
	 * @param event the event, whose reason is why it was refused
	 * @param exception the exception the caller throws
	 * @return the exception
	 * @throws IllegalArgumentException if the event has no reason
	 */
	public <X extends RuntimeException> X reject(AuditEvent event, Supplier<X> exception) {
		if (event.reasonCode() == null || event.reasonCode().isBlank()) {
			throw new IllegalArgumentException("A refused change needs a reason.");
		}
		AuditTrailEvent row = row(event, AuditOutcome.FAILURE);
		AuditLog.write(event, row);
		try {
			this.rows.saveInNewTransaction(row);
		}
		catch (RuntimeException ex) {
			AuditLog.rowNotWritten(row, ex);
		}
		return exception.get();
	}

	/**
	 * Reads events back, oldest first.
	 * @param query the criteria
	 * @return the events
	 */
	public List<AuditRecord> find(AuditQuery query) {
		return this.rows.find(query).stream().map(AuditRecord::new).toList();
	}

	/**
	 * Lists events for a person.
	 * @param search the filters
	 * @param pageable the page and sort, by {@code occurredAt}
	 * @return the page of events
	 */
	public Page<AuditRecord> search(AuditSearch search, Pageable pageable) {
		return this.rows.search(search, pageable).map(AuditRecord::new);
	}

	private AuditTrailEvent row(AuditEvent event, AuditOutcome outcome) {
		String details = (event.details() != null) ? AuditRecord.JSON.writeValueAsString(event.details()) : null;
		return new AuditTrailEvent(this.clock.instant(), Auditor.current(), event.action().name(), outcome,
				event.target(), event.reasonCode(), event.reasonNote(), details);
	}

	/**
	 * Runs the logging after the current transaction commits, on the same thread so the
	 * request's correlation fields are kept, or now if there is no transaction.
	 */
	private static void afterCommit(Runnable log) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void afterCommit() {
					log.run();
				}

			});
		}
		else {
			log.run();
		}
	}

}
