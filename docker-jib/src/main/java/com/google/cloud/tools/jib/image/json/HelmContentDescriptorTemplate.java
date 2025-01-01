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
package com.google.cloud.tools.jib.image.json;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate.ContentDescriptorTemplate;

import lombok.Data;

@Data
public class HelmContentDescriptorTemplate extends ContentDescriptorTemplate {

	private Map<String, String> annotations = new LinkedHashMap<>();

	void addAnnotation(final String key, final String value) {
		annotations.put(key, value);
	}

	public HelmContentDescriptorTemplate(final String layerMediaType, final long size, final DescriptorDigest digest, final Map<String, String> annotations) {
		super(layerMediaType, size, digest);
		this.annotations = annotations;
	}
}
