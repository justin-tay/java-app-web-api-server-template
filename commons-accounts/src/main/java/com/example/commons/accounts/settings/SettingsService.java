package com.example.commons.accounts.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.domain.AppSetting;
import com.example.commons.accounts.domain.AppSettingRepository;
import com.example.commons.web.problem.BadRequestException;

/**
 * Reads and changes the application settings. A change is audited with the old and new
 * values. A setting with no row falls back to its default, so a missing row never turns
 * the inactivity job off.
 */
@Transactional
public class SettingsService {

	static final String INACTIVITY_ENABLED = "inactivity.enabled";

	static final String SUSPEND_AFTER_DAYS = "inactivity.suspendAfterDays";

	static final String REMOVE_AFTER_DAYS = "inactivity.removeAfterDays";

	static final String REVIEW_ENABLED = "review.enabled";

	static final String REVIEW_PRIVILEGED_INTERVAL_MONTHS = "review.privilegedIntervalMonths";

	static final String REVIEW_NON_PRIVILEGED_INTERVAL_MONTHS = "review.nonPrivilegedIntervalMonths";

	private static final Set<Integer> VALID_INTERVALS = Set.of(1, 3, 6, 12);

	private final AppSettingRepository settings;

	private final AccountAuditLogger auditLogger;

	public SettingsService(AppSettingRepository settings, AccountAuditLogger auditLogger) {
		this.settings = settings;
		this.auditLogger = auditLogger;
	}

	@Transactional(readOnly = true)
	public Settings get() {
		return toSettings(values());
	}

	public Settings update(Settings requested) {
		if (requested.inactivity().removeAfterDays() <= requested.inactivity().suspendAfterDays()) {
			throw new BadRequestException("inactivity.removeAfterDays",
					"The removal threshold must be greater than the suspension threshold.");
		}
		if (!VALID_INTERVALS.contains(requested.review().privilegedIntervalMonths())) {
			throw new BadRequestException("review.privilegedIntervalMonths",
					"A review interval must be 1, 3, 6 or 12 months.");
		}
		if (!VALID_INTERVALS.contains(requested.review().nonPrivilegedIntervalMonths())) {
			throw new BadRequestException("review.nonPrivilegedIntervalMonths",
					"A review interval must be 1, 3, 6 or 12 months.");
		}
		if (requested.review().nonPrivilegedIntervalMonths() < requested.review().privilegedIntervalMonths()) {
			throw new BadRequestException("review.nonPrivilegedIntervalMonths",
					"The non-privileged review interval cannot be shorter than the privileged review interval.");
		}
		Map<String, String> before = values();
		Map<String, String> after = toValues(requested);
		Map<String, Object> changes = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : after.entrySet()) {
			String previous = before.get(entry.getKey());
			if (!entry.getValue().equals(previous)) {
				AppSetting setting = this.settings.findByName(entry.getKey())
					.orElseGet(() -> new AppSetting(entry.getKey(), entry.getValue()));
				setting.change(entry.getValue());
				this.settings.save(setting);
				changes.put(entry.getKey(), Map.of("from", String.valueOf(previous), "to", entry.getValue()));
			}
		}
		if (!changes.isEmpty()) {
			this.auditLogger.record("update_setting", "SETTING", "settings", "settings", null, null,
					Map.of("changes", changes));
		}
		return requested;
	}

	private Map<String, String> values() {
		Map<String, String> values = this.settings.findAll()
			.stream()
			.collect(Collectors.toMap(AppSetting::getName, AppSetting::getValue, (a, b) -> a, LinkedHashMap::new));
		defaults().forEach(values::putIfAbsent);
		return values;
	}

	private static Settings toSettings(Map<String, String> values) {
		Function<String, String> value = name -> values.getOrDefault(name, defaults().get(name));
		return new Settings(new Settings.Inactivity(Boolean.parseBoolean(value.apply(INACTIVITY_ENABLED)),
				Integer.parseInt(value.apply(SUSPEND_AFTER_DAYS)), Integer.parseInt(value.apply(REMOVE_AFTER_DAYS))),
				new Settings.Review(Boolean.parseBoolean(value.apply(REVIEW_ENABLED)),
						Integer.parseInt(value.apply(REVIEW_PRIVILEGED_INTERVAL_MONTHS)),
						Integer.parseInt(value.apply(REVIEW_NON_PRIVILEGED_INTERVAL_MONTHS))));
	}

	private static Map<String, String> toValues(Settings settings) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(INACTIVITY_ENABLED, String.valueOf(settings.inactivity().enabled()));
		values.put(SUSPEND_AFTER_DAYS, String.valueOf(settings.inactivity().suspendAfterDays()));
		values.put(REMOVE_AFTER_DAYS, String.valueOf(settings.inactivity().removeAfterDays()));
		values.put(REVIEW_ENABLED, String.valueOf(settings.review().enabled()));
		values.put(REVIEW_PRIVILEGED_INTERVAL_MONTHS, String.valueOf(settings.review().privilegedIntervalMonths()));
		values.put(REVIEW_NON_PRIVILEGED_INTERVAL_MONTHS,
				String.valueOf(settings.review().nonPrivilegedIntervalMonths()));
		return values;
	}

	private static Map<String, String> defaults() {
		return Map.of(INACTIVITY_ENABLED, "true", SUSPEND_AFTER_DAYS, "90", REMOVE_AFTER_DAYS, "180", REVIEW_ENABLED,
				"true", REVIEW_PRIVILEGED_INTERVAL_MONTHS, "1", REVIEW_NON_PRIVILEGED_INTERVAL_MONTHS, "12");
	}

}
