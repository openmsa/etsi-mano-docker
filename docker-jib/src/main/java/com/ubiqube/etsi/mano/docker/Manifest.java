package com.ubiqube.etsi.mano.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;

import lombok.Data;

@Data
public class Manifest {
	private ObjectMapper mapper;
	private String content;

	public Manifest(final ObjectMapper mapper, final String content) {
		this.mapper = mapper;
		this.content = content;
	}

	public String getRaw() {
		return content;
	}

	public DockerManifestEntryTemplate get() {
		try {
			return mapper.readValue(content, DockerManifestEntryTemplate[].class)[0];
		} catch (final JsonProcessingException e) {
			throw new DockerApiException(e);
		}
	}

}
