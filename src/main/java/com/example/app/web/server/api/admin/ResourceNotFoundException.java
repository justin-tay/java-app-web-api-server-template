package com.example.app.web.server.api.admin;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource) {
		super(resource + " was not found.");
	}

}
