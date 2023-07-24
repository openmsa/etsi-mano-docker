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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;

public class DockerTarFile implements ContainerTarFile {
	private static final Logger LOG = LoggerFactory.getLogger(DockerTarFile.class);
	private final ObjectMapper mapper = JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();
	private final List<LayerDescriptor> layers;
	private final byte[] configRaw;
	private final Manifest mf;

	private final ArchiveApi aa;

	public DockerTarFile(final TarFile tarFile) {
		this.aa = new ArchiveApi(tarFile);
		this.mf = openManifest();
		final DockerManifestEntryTemplate manifestEntry = mf.get();
		this.configRaw = getConfigRaw(manifestEntry.getConfig());
		this.layers = getLayers(configRaw);

	}

	@Override
	public void copyTo(final Registry reg, final String tag) {
		final BuildableManifestTemplate mft = new V22ManifestTemplate();
		layers.forEach(x -> {
			try (final InputStream is = aa.getInputStream(x.blob())) {
				final long sz = reg.pushBlob(is, x.digest());
				mft.addLayer(sz, x.digest());
			} catch (final IOException e) {
				throw new DockerApiException(e);
			}
		});
		final DescriptorDigest manifestDigest = reg.pushConfig(configRaw);
		mft.setContainerConfiguration(configRaw.length, manifestDigest);
		//
		final ContainerConfigurationTemplate r2 = new ContainerConfigurationTemplate();
		layers.forEach(x -> r2.addLayerDiffId(x.digest()));
		LOG.debug("Pushing maifest with tag {}", tag);
		reg.pushManifest(mft, tag);

	}

	private byte[] getConfigRaw(final String config) {
		return aa.getContent(config);
	}

	private List<LayerDescriptor> getLayers(final byte[] content) {
		ContainerConfigurationTemplate conf;
		try {
			conf = mapper.readValue(content, ContainerConfigurationTemplate.class);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
		final ArrayList<LayerDescriptor> ret = new ArrayList<>();
		final List<String> layerFiles = mf.get().getLayerFiles();
		if (layerFiles.size() != conf.getLayerCount()) {
			throw new DockerApiException("Number of layer missmatch " + layerFiles.size() + "!=" + conf.getLayerCount());
		}
		for (int i = 0; i < conf.getLayerCount(); i++) {
			ret.add(new LayerDescriptor(layerFiles.get(i), conf.getLayerDiffId(i)));
		}
		return ret;
	}

	private Manifest openManifest() {
		final byte[] content = aa.getContent("manifest.json");
		return new Manifest(mapper, new String(content));
	}
}
