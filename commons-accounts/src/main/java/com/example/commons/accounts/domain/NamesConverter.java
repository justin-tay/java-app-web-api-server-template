package com.example.commons.accounts.domain;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Holds a list of names as a JSON array in a text column. A review keeps role names and
 * permissions as text, not as references, so renaming or deleting a role does not change
 * what a reviewer saw.
 */
@Converter
public class NamesConverter implements AttributeConverter<List<String>, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final TypeReference<List<String>> NAMES = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<String> names) {
		return names == null ? null : MAPPER.writeValueAsString(names);
	}

	@Override
	public List<String> convertToEntityAttribute(String json) {
		return json == null ? null : MAPPER.readValue(json, NAMES);
	}

}
