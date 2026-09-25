package com.example.commons.accounts;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * The configuration {@link AccountsJpaTest} finds by searching up from a test's package.
 * Its package, {@code com.example.commons.accounts}, is the auto-configuration package,
 * so the entities and repositories in {@code domain} are scanned.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class AccountsTestConfiguration {

}
