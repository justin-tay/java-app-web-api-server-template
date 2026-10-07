package com.example.commons.audit;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * The configuration the audit tests find by searching up from their package. Its package,
 * {@code com.example.commons.audit}, is the auto-configuration package, so the entity and
 * the repository are scanned.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class AuditTestConfiguration {

}
