package com.example.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class LoggedUrlPathTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			/login-user                                   | /login-user
			/                                             | /
			/login-user;jsessionid=0f5b3c1e               | /login-user;[REDACTED]
			/login-user;id=0f5b3c1e;other=1               | /login-user;[REDACTED]
			/admin;jsessionid=0f5b3c1e/users              | /admin;[REDACTED]/users
			/admin;a=1/users;b=2/42                       | /admin;[REDACTED]/users;[REDACTED]/42
			/;jsessionid=0f5b3c1e                         | /;[REDACTED]
			/login-user;                                  | /login-user;[REDACTED]
			""")
	void redactsEveryPathParameterSection(String path, String expected) {
		assertThat(LoggedUrlPath.of(path)).isEqualTo(expected);
	}

	@Test
	void keepsANullPathNull() {
		assertThat(LoggedUrlPath.of(null)).isNull();
	}

}
