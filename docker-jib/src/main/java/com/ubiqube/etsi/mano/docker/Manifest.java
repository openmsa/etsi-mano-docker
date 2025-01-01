/**
 * Copyright (C) 2019-2025 Ubiqube.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
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
