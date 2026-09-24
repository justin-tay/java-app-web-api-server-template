package com.example.app.web.server.api.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void userListRequiresUserManagementRole() throws Exception {
		this.mockMvc.perform(get("/admin/users").with(user("test"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/admin/users").with(user("admin").roles("USER_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void userListHonoursSupportedSortAndRejectsUnsupportedSort() throws Exception {
		this.mockMvc
			.perform(get("/admin/users").param("sort", "username,desc").with(user("admin").roles("USER_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].username").value("test-user"));
		this.mockMvc.perform(get("/admin/users").param("sort", "password,asc").with(user("admin").roles("USER_MANAGE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").doesNotExist());
	}

	@Test
	void invalidUserRequestReturnsPointerError() throws Exception {
		this.mockMvc
			.perform(post("/admin/users").with(user("admin").roles("USER_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"\",\"displayName\":\"\",\"email\":null,\"enabled\":false,\"groupIds\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").exists());
	}

}
