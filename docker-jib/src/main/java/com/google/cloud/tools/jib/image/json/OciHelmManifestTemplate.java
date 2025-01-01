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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.cloud.tools.jib.api.DescriptorDigest;

import org.jspecify.annotations.Nullable;

public class OciHelmManifestTemplate implements BuildableManifestTemplate {

	/** The OCI manifest media type. */
	public static final String MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";

	/** The OCI container configuration media type. */
	private static final String CONTAINER_CONFIGURATION_MEDIA_TYPE = "application/vnd.cncf.helm.config.v1+json";

	/** The OCI layer media type. */
	private static final String LAYER_MEDIA_TYPE = "application/vnd.cncf.helm.chart.content.v1.tar+gzip";

	private final int schemaVersion = 2;

	@SuppressWarnings("unused")
	private final String mediaType = MANIFEST_MEDIA_TYPE;

	/** The container configuration reference. */
	@Nullable
	private ContentDescriptorTemplate config;

	/** The list of layer references. */
	private final List<ContentDescriptorTemplate> layers = new ArrayList<>();

	@Override
	public int getSchemaVersion() {
		return schemaVersion;
	}

	@Override
	public String getManifestMediaType() {
		return MANIFEST_MEDIA_TYPE;
	}

	@Override
	@Nullable
	public ContentDescriptorTemplate getContainerConfiguration() {
		return config;
	}

	@Override
	public List<ContentDescriptorTemplate> getLayers() {
		return Collections.unmodifiableList(layers);
	}

	@Override
	public void setContainerConfiguration(final long size, final DescriptorDigest digest) {
		config = new ContentDescriptorTemplate(CONTAINER_CONFIGURATION_MEDIA_TYPE, size, digest);
	}

	@Override
	public void addLayer(final long size, final DescriptorDigest digest) {
		layers.add(new ContentDescriptorTemplate(LAYER_MEDIA_TYPE, size, digest));
	}

	public void addLayer(final long size, final DescriptorDigest digest, final Map<String, String> annotations) {
		layers.add(new HelmContentDescriptorTemplate(LAYER_MEDIA_TYPE, size, digest, annotations));
	}
}
