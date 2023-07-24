/**
 *     Copyright (C) 2019-2023 Ubiqube.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see https://www.gnu.org/licenses/.
 */
package com.ubiqube.etsi.mano.docker;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarFile;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;

public class OciTarFile implements ContainerTarFile {
	private final ObjectMapper mapper = JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();
	private final ManifestDescriptorTemplate mf;
	private final OciManifestTemplate config;
	private final byte[] configRaw;
	private final String configHash;
	private final ArchiveApi aa;

	public OciTarFile(final TarFile tarFile) {
		this.aa = new ArchiveApi(tarFile);
		final OciIndexTemplate ociIndex;
		try (final InputStream indexIs = aa.getInputStream("index.json")) {
			ociIndex = mapper.readValue(indexIs, OciIndexTemplate.class);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
		if (2 != ociIndex.getSchemaVersion()) {
			throw new DockerApiException("Invalid OCI schema version: " + ociIndex.getSchemaVersion());
		}
		final List<ManifestDescriptorTemplate> mfs = ociIndex.getManifests();
		if (mfs.size() != 1) {
			throw new DockerApiException("Only single OCI manifest is supported.");
		}
		this.mf = mfs.get(0);
		this.configHash = Optional.ofNullable(mf.getDigest()).map(DescriptorDigest::getHash).orElseThrow();
		final byte[] blob = aa.getContent("blobs/sha256/" + configHash);
		this.configRaw = blob;
		try {
			this.config = mapper.readValue(blob, OciManifestTemplate.class);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	@Override
	public void copyTo(final Registry reg, final String tag) {
		final OciManifestTemplate mft = new OciManifestTemplate();
		config.getLayers().forEach(x -> {
			final String digest = Optional.ofNullable(x.getDigest()).map(DescriptorDigest::getHash).orElseThrow();
			try (final InputStream blobis = aa.getInputStream("blobs/sha256/" + digest)) {
				final long sz = reg.pushBlob(blobis, x.getDigest());
				mft.addLayer(sz, x.getDigest());
			} catch (final IOException e) {
				throw new DockerApiException(e);
			}
		});
		try {
			mft.setContainerConfiguration(configRaw.length, DescriptorDigest.fromHash(configHash));
		} catch (final DigestException e) {
			throw new DockerApiException(e);
		}
		reg.pushConfig(configRaw);
		reg.pushManifest(mft, tag);
	}
}
