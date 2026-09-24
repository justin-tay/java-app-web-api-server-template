package com.example.commons.web.problem;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource) {
		super(resource + " was not found.");
	}

}
