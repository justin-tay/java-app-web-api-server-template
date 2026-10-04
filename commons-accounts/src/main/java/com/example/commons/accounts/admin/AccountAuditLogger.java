package com.example.commons.accounts.admin;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.Auditor;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.logging.LoggingContextKeys;

/**
 * Records every change to the local accounts, groups, roles, settings, and reviews. Each
 * is logged as an ECS {@code iam} event, so who granted or withdrew which access, and
 * when, can be reconstructed from the logs alone (see docs/adr/0021), and, when a
 * repository is supplied, appended to the business audit trail that the application
 * itself shows (see docs/adr/0030).
 *
 * <p>
 * Following ECS's user field usage, {@code user.target.*}, {@code group.*}, and
 * {@code role.*} hold an object's state before the change, and {@code *.changes.*} hold
 * only the values that changed, so every event is self-contained. Role values are the
 * stored role names, never the {@code ROLE_}-prefixed authorities, and
 * {@code roles.added}, {@code roles.removed}, {@code groups.added}, and
 * {@code groups.removed} list the access each change grants or withdraws. The values of
 * personal-data fields are never logged, only the names of the ones that changed.
 *
 * <p>
 * A successful change is logged after its transaction commits, on the same thread, so a
 * change that is rolled back is never logged and the request's correlation fields are
 * kept. A change rejected by a business rule is logged immediately.
 */
public class AccountAuditLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccountAuditLogger.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AccountAuditEventRepository events;

	private final Clock clock;

	/**
	 * Creates a logger that only writes log events, with no audit trail table.
	 */
	public AccountAuditLogger() {
		this(null, Clock.systemUTC());
	}

	/**
	 * Creates a logger that also appends each successful change to the audit trail, in
	 * the caller's transaction.
	 * @param events the audit event repository, or null for log events only
	 * @param clock the clock that stamps each event
	 */
	public AccountAuditLogger(AccountAuditEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	/**
	 * A user's security-relevant state.
	 *
	 * @param id the user ID
	 * @param username the username
	 * @param status the account status, {@code active} or {@code suspended}
	 * @param groups the names of the user's groups
	 * @param roles the names of the roles the user's groups grant
	 * @param email the email address, compared but never logged
	 * @param name the name, compared but never logged
	 */
	public record UserState(UUID id, String username, String status, SortedSet<String> groups, SortedSet<String> roles,
			String email, String name) {

		public static UserState of(AppUser user) {
			return new UserState(user.getPublicId(), user.getUsername(),
					user.getStatus().name().toLowerCase(Locale.ROOT),
					names(user.getGroups().stream().map(AppGroup::getName).toList()),
					names(user.getGroups()
						.stream()
						.flatMap(group -> group.getRoles().stream())
						.map(AppRole::getName)
						.toList()),
					user.getEmail(), user.getName());
		}

	}

	/**
	 * A group's security-relevant state.
	 *
	 * @param id the group ID
	 * @param name the group name
	 * @param roles the names of the roles the group grants
	 */
	public record GroupState(UUID id, String name, SortedSet<String> roles) {

		static GroupState of(AppGroup group) {
			return new GroupState(group.getPublicId(), group.getName(),
					names(group.getRoles().stream().map(AppRole::getName).toList()));
		}

	}

	/**
	 * A role's state.
	 *
	 * @param id the role ID
	 * @param name the role name
	 */
	public record RoleState(UUID id, String name) {

		static RoleState of(AppRole role) {
			return new RoleState(role.getPublicId(), role.getName());
		}

	}

	public void userCreated(UserState user) {
		record("create_user", "USER", user, null, null, details("groups", user.groups(), "roles", user.roles()));
		afterCommit(() -> {
			LoggingEventBuilder event = event("create_user", "user", "creation", user.username());
			target(event, user);
			event.addKeyValue("groups.added", List.copyOf(user.groups()))
				.addKeyValue("roles.added", List.copyOf(user.roles()))
				.log("User created");
		});
	}

	public void userUpdated(UserState before, UserState after) {
		userUpdated(before, after, null);
	}

	/**
	 * Records a user change made by the application itself, with the reason it was made.
	 * @param before the user's state before the change
	 * @param after the user's state after the change
	 * @param reason the controlled reason, such as {@code dormant_account}, or null
	 */
	public void userUpdated(UserState before, UserState after, String reason) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("before", Map.of("status", before.status(), "groups", before.groups(), "roles", before.roles()));
		List<String> changedFields = new ArrayList<>();
		if (!Objects.equals(before.email(), after.email())) {
			changedFields.add("email");
		}
		if (!Objects.equals(before.name(), after.name())) {
			changedFields.add("name");
		}
		details.put("fields", changedFields);
		details.put("groupsAdded", added(before.groups(), after.groups()));
		details.put("groupsRemoved", added(after.groups(), before.groups()));
		details.put("rolesAdded", added(before.roles(), after.roles()));
		details.put("rolesRemoved", added(after.roles(), before.roles()));
		record("update_user", "USER", after, reason, null, details);
		afterCommit(() -> {
			LoggingEventBuilder event = event("update_user", "user", "change", before.username());
			if (reason != null) {
				event.addKeyValue("event.reason", reason);
			}
			target(event, before);
			if (!before.status().equals(after.status())) {
				event.addKeyValue("user.changes.status", after.status());
			}
			if (!before.groups().equals(after.groups())) {
				event.addKeyValue("user.changes.group.name", List.copyOf(after.groups()));
			}
			if (!before.roles().equals(after.roles())) {
				event.addKeyValue("user.changes.roles", List.copyOf(after.roles()));
			}
			List<String> fields = new ArrayList<>();
			if (!Objects.equals(before.email(), after.email())) {
				fields.add("email");
			}
			if (!Objects.equals(before.name(), after.name())) {
				fields.add("full_name");
			}
			if (!fields.isEmpty()) {
				event.addKeyValue("user.changes.fields", fields);
			}
			event.addKeyValue("groups.added", added(before.groups(), after.groups()))
				.addKeyValue("groups.removed", added(after.groups(), before.groups()))
				.addKeyValue("roles.added", added(before.roles(), after.roles()))
				.addKeyValue("roles.removed", added(after.roles(), before.roles()))
				.log("User updated");
		});
	}

	/**
	 * Records an account being removed, with the reason it was removed.
	 * @param user the account's state before the removal
	 * @param reason the reason code
	 * @param note the optional note, or null
	 */
	public void userDeleted(UserState user, ReasonCode reason, String note) {
		record("delete_user", "USER", user, reason.value(), note,
				details("status", user.status(), "groups", user.groups(), "roles", user.roles()));
		afterCommit(() -> {
			LoggingEventBuilder event = event("delete_user", "user", "deletion", user.username());
			event.addKeyValue("event.reason", reason.value());
			target(event, user);
			event.addKeyValue("groups.removed", List.copyOf(user.groups()))
				.addKeyValue("roles.removed", List.copyOf(user.roles()))
				.log("User deleted");
		});
	}

	/**
	 * Records an account being suspended.
	 * @param before the account's state before the change
	 * @param after the account's state after the change
	 * @param reason the reason code
	 * @param note the optional note, or null
	 */
	public void userSuspended(UserState before, UserState after, ReasonCode reason, String note) {
		record("suspend_user", "USER", after, reason.value(), note, details("before", before.status()));
		afterCommit(() -> {
			LoggingEventBuilder event = event("suspend_user", "user", "change", before.username());
			event.addKeyValue("event.reason", reason.value());
			target(event, before);
			event.addKeyValue("user.changes.status", after.status()).log("User suspended");
		});
	}

	public void userUnsuspended(UserState before, UserState after) {
		record("unsuspend_user", "USER", after, null, null, details("before", before.status()));
		afterCommit(() -> {
			LoggingEventBuilder event = event("unsuspend_user", "user", "change", before.username());
			target(event, before);
			event.addKeyValue("user.changes.status", after.status()).log("User unsuspended");
		});
	}

	public void userUpdateRejected(UserState user, String reason) {
		LoggingEventBuilder event = rejected("update_user", "user", "change", user.username(), reason);
		target(event, user);
		event.log("User update rejected");
	}

	public void userDeletionRejected(UserState user, String reason) {
		LoggingEventBuilder event = rejected("delete_user", "user", "deletion", user.username(), reason);
		target(event, user);
		event.log("User deletion rejected");
	}

	public void userCreationRejected(String username, String reason) {
		rejected("create_user", "user", "creation", username, reason).addKeyValue("user.target.name", username)
			.log("User creation rejected");
	}

	/**
	 * Records an administrator ending sessions: one user's, or, when {@code username} is
	 * null, every user's but their own. Each ended session is also logged as a
	 * {@code destroy_session} event with reason {@code administrative_revocation}.
	 * @param username the user whose sessions were ended, or null for every user
	 * @param revoked the number of sessions ended
	 */
	public void sessionsRevoked(String username, int revoked) {
		LoggingEventBuilder event = event("revoke_sessions", "user", "change", username);
		if (username != null) {
			event.addKeyValue("user.target.name", username);
		}
		event.addKeyValue("session.revoked_count", revoked).log("Sessions revoked");
	}

	public void groupCreated(GroupState group) {
		record("create_group", "GROUP", group.id().toString(), group.name(), null, null, null,
				details("rolesAdded", group.roles()));
		afterCommit(() -> group(event("create_group", "group", "creation", null), group)
			.addKeyValue("roles.added", List.copyOf(group.roles()))
			.log("Group created"));
	}

	public void groupUpdated(GroupState before, GroupState after, long affectedUserCount) {
		record("update_group", "GROUP", after.id().toString(), after.name(), null, null, null,
				details("beforeName", before.name(), "rolesAdded", added(before.roles(), after.roles()), "rolesRemoved",
						added(after.roles(), before.roles()), "affectedUserCount", affectedUserCount));
		afterCommit(() -> {
			LoggingEventBuilder event = group(event("update_group", "group", "change", null), before);
			if (!before.name().equals(after.name())) {
				event.addKeyValue("group.changes.name", after.name());
			}
			if (!before.roles().equals(after.roles())) {
				event.addKeyValue("group.changes.roles", List.copyOf(after.roles()));
			}
			event.addKeyValue("group.affected_user_count", affectedUserCount)
				.addKeyValue("roles.added", added(before.roles(), after.roles()))
				.addKeyValue("roles.removed", added(after.roles(), before.roles()))
				.log("Group updated");
		});
	}

	public void groupDeleted(GroupState group) {
		record("delete_group", "GROUP", group.id().toString(), group.name(), null, null, null,
				details("rolesRemoved", group.roles()));
		afterCommit(() -> group(event("delete_group", "group", "deletion", null), group)
			.addKeyValue("roles.removed", List.copyOf(group.roles()))
			.log("Group deleted"));
	}

	public void groupCreationRejected(String name, String reason) {
		rejected("create_group", "group", "creation", null, reason).addKeyValue("group.name", name)
			.log("Group creation rejected");
	}

	public void groupUpdateRejected(GroupState group, String requestedName, String reason) {
		LoggingEventBuilder event = group(rejected("update_group", "group", "change", null, reason), group);
		if (!group.name().equals(requestedName)) {
			event.addKeyValue("group.changes.name", requestedName);
		}
		event.log("Group update rejected");
	}

	public void groupDeletionRejected(GroupState group, String reason) {
		group(rejected("delete_group", "group", "deletion", null, reason), group).log("Group deletion rejected");
	}

	public void roleCreated(RoleState role) {
		record("create_role", "ROLE", role.id().toString(), role.name(), null, null, null, details());
		afterCommit(() -> role(event("create_role", "admin", "creation", null), role).log("Role created"));
	}

	public void roleDeleted(RoleState role) {
		record("delete_role", "ROLE", role.id().toString(), role.name(), null, null, null, details());
		afterCommit(() -> role(event("delete_role", "admin", "deletion", null), role).log("Role deleted"));
	}

	public void roleCreationRejected(String name, String reason) {
		rejected("create_role", "admin", "creation", null, reason).addKeyValue("role.name", name)
			.log("Role creation rejected");
	}

	public void roleDeletionRejected(RoleState role, String reason) {
		role(rejected("delete_role", "admin", "deletion", null, reason), role).log("Role deletion rejected");
	}

	/**
	 * Appends an audit event for a change to the settings or a review, which the other
	 * methods do not cover.
	 * @param action the action, such as {@code update_setting}
	 * @param targetType the target type, {@code SETTING} or {@code REVIEW}
	 * @param targetId the target ID
	 * @param targetName the target name
	 * @param reasonCode the reason code, or null
	 * @param reasonNote the note, or null
	 * @param details the changed values
	 */
	public void record(String action, String targetType, String targetId, String targetName, String reasonCode,
			String reasonNote, Map<String, Object> details) {
		record(action, targetType, targetId, targetName, null, reasonCode, reasonNote, details);
	}

	private void record(String action, String targetType, UserState user, String reasonCode, String reasonNote,
			Map<String, Object> details) {
		record(action, targetType, user.id().toString(), user.username(), user.name(), reasonCode, reasonNote, details);
	}

	private void record(String action, String targetType, String targetId, String targetName, String targetFullName,
			String reasonCode, String reasonNote, Map<String, Object> details) {
		if (this.events == null) {
			return;
		}
		this.events.save(new AccountAuditEvent(this.clock.instant(), Auditor.current(), action, targetType, targetId,
				targetName, targetFullName, reasonCode, reasonNote, JSON.writeValueAsString(details)));
	}

	/**
	 * Builds an ordered details object from alternating keys and values.
	 */
	private static Map<String, Object> details(Object... keysAndValues) {
		Map<String, Object> details = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			details.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return details;
	}

	private static LoggingEventBuilder event(String action, String object, String type, String targetUsername) {
		return categorized(LOGGER.atInfo(), action, object, type, targetUsername).addKeyValue("event.outcome",
				"success");
	}

	private static LoggingEventBuilder rejected(String action, String object, String type, String targetUsername,
			String reason) {
		return categorized(LOGGER.atWarn(), action, object, type, targetUsername)
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("event.reason", reason);
	}

	private static LoggingEventBuilder categorized(LoggingEventBuilder event, String action, String object, String type,
			String targetUsername) {
		event.addKeyValue("event.category", List.of("iam"))
			.addKeyValue("event.type", List.of(object, type))
			.addKeyValue("event.action", action);
		String actor = Auditor.current();
		boolean authenticated = !Auditor.SYSTEM.equals(actor);
		if (authenticated && MDC.get(LoggingContextKeys.USER_NAME) == null) {
			event.addKeyValue(LoggingContextKeys.USER_NAME, actor);
		}
		SortedSet<String> related = new TreeSet<>();
		if (authenticated) {
			related.add(actor);
		}
		if (targetUsername != null) {
			related.add(targetUsername);
		}
		if (!related.isEmpty()) {
			event.addKeyValue("related.user", List.copyOf(related));
		}
		return event;
	}

	private static void target(LoggingEventBuilder event, UserState user) {
		event.addKeyValue("user.target.id", user.id())
			.addKeyValue("user.target.name", user.username())
			.addKeyValue("user.target.status", user.status())
			.addKeyValue("user.target.group.name", List.copyOf(user.groups()))
			.addKeyValue("user.target.roles", List.copyOf(user.roles()));
	}

	private static LoggingEventBuilder group(LoggingEventBuilder event, GroupState group) {
		return event.addKeyValue("group.id", group.id())
			.addKeyValue("group.name", group.name())
			.addKeyValue("group.roles", List.copyOf(group.roles()));
	}

	private static LoggingEventBuilder role(LoggingEventBuilder event, RoleState role) {
		return event.addKeyValue("role.id", role.id()).addKeyValue("role.name", role.name());
	}

	/**
	 * Returns the sorted values in {@code after} that are not in {@code before}.
	 */
	private static List<String> added(Set<String> before, Set<String> after) {
		return after.stream().filter(value -> !before.contains(value)).sorted().toList();
	}

	private static SortedSet<String> names(Collection<String> names) {
		return new TreeSet<>(names);
	}

	/**
	 * Runs the logging after the current transaction commits, or now if there is none.
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
