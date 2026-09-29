package com.example.commons.accounts.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.Auditor;
import com.example.commons.logging.LoggingContextKeys;

/**
 * Records every change to the local user, group, and role model as an ECS {@code iam}
 * event, so who granted or withdrew which access, and when, can be reconstructed from the
 * logs alone (see docs/adr/0021).
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
public class AdministrationAuditLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdministrationAuditLogger.class);

	/**
	 * A user's security-relevant state.
	 *
	 * @param id the user ID
	 * @param username the username
	 * @param enabled whether the user is enabled
	 * @param groups the names of the user's groups
	 * @param roles the names of the roles the user's groups grant
	 * @param email the email address, compared but never logged
	 * @param name the name, compared but never logged
	 */
	public record UserState(String id, String username, boolean enabled, SortedSet<String> groups,
			SortedSet<String> roles, String email, String name) {

		static UserState of(AppUser user) {
			return new UserState(user.getId(), user.getUsername(), user.isEnabled(),
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
	public record GroupState(String id, String name, SortedSet<String> roles) {

		static GroupState of(AppGroup group) {
			return new GroupState(group.getId(), group.getName(),
					names(group.getRoles().stream().map(AppRole::getName).toList()));
		}

	}

	/**
	 * A role's state.
	 *
	 * @param id the role ID
	 * @param name the role name
	 */
	public record RoleState(String id, String name) {

		static RoleState of(AppRole role) {
			return new RoleState(role.getId(), role.getName());
		}

	}

	public void userCreated(UserState user) {
		afterCommit(() -> {
			LoggingEventBuilder event = event("create_user", "user", "creation", user.username());
			target(event, user);
			event.addKeyValue("groups.added", List.copyOf(user.groups()))
				.addKeyValue("roles.added", List.copyOf(user.roles()))
				.log("User created");
		});
	}

	public void userUpdated(UserState before, UserState after) {
		afterCommit(() -> {
			LoggingEventBuilder event = event("update_user", "user", "change", before.username());
			target(event, before);
			if (before.enabled() != after.enabled()) {
				event.addKeyValue("user.changes.enabled", after.enabled());
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

	public void userDeleted(UserState user) {
		afterCommit(() -> {
			LoggingEventBuilder event = event("delete_user", "user", "deletion", user.username());
			target(event, user);
			event.addKeyValue("groups.removed", List.copyOf(user.groups()))
				.addKeyValue("roles.removed", List.copyOf(user.roles()))
				.log("User deleted");
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
		afterCommit(() -> group(event("create_group", "group", "creation", null), group)
			.addKeyValue("roles.added", List.copyOf(group.roles()))
			.log("Group created"));
	}

	public void groupUpdated(GroupState before, GroupState after, long affectedUserCount) {
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
		afterCommit(() -> role(event("create_role", "admin", "creation", null), role).log("Role created"));
	}

	public void roleDeleted(RoleState role) {
		afterCommit(() -> role(event("delete_role", "admin", "deletion", null), role).log("Role deleted"));
	}

	public void roleCreationRejected(String name, String reason) {
		rejected("create_role", "admin", "creation", null, reason).addKeyValue("role.name", name)
			.log("Role creation rejected");
	}

	public void roleDeletionRejected(RoleState role, String reason) {
		role(rejected("delete_role", "admin", "deletion", null, reason), role).log("Role deletion rejected");
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
			.addKeyValue("user.target.enabled", user.enabled())
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
