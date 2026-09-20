package com.example.app.web.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemDetailErrorControllerTest {

	private final ProblemDetailErrorController controller = new ProblemDetailErrorController();

	@Test
	void mapsNotFoundStatusToRouteNotFoundType() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());

		ResponseEntity<ProblemDetail> response = this.controller.handleError(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().getType()).isEqualTo(ProblemTypes.ROUTE_NOT_FOUND);
	}

	@Test
	void mapsMethodNotAllowedStatusToMethodNotAllowedType() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.METHOD_NOT_ALLOWED.value());

		ResponseEntity<ProblemDetail> response = this.controller.handleError(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getBody().getType()).isEqualTo(ProblemTypes.METHOD_NOT_ALLOWED);
	}

	@Test
	void fallsBackToInternalErrorForAnUnmappedOrMissingStatus() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		ResponseEntity<ProblemDetail> response = this.controller.handleError(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getType()).isEqualTo(ProblemTypes.INTERNAL_ERROR);
	}

}
