/**
 *     Copyright (C) 2019-2024 Ubiqube.
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.compress.archivers.tar.TarFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate.ContentDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.OciHelmManifestTemplate;
import com.ubiqube.etsi.mano.helm.Chart;

public class OciHelmTarFile implements ContainerTarFile {
	private final ObjectMapper json = new ObjectMapper();
	private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
	private final ArchiveApi aa;
	private final OciHelmManifestTemplate omt;
	private final Chart chart;
	private final File file;
	private final String config;

	public OciHelmTarFile(final File file) {
		this.file = verify(file);
		try (TarFile tf = new TarFile(file)) {
			this.aa = new ArchiveApi(tf);
			final String entry = pickRootChart();
			this.chart = loadChart(entry);
			this.config = chartToJsonString();
			final String sha256 = toSha256(config);
			//
			this.omt = new OciHelmManifestTemplate();
			omt.setContainerConfiguration(config.length(), DescriptorDigest.fromDigest("sha256:" + sha256));
			final String sha256File = toSha256(file);
			omt.addLayer(file.length(), DescriptorDigest.fromDigest("sha256:" + sha256File), Map.of("org.opencontainers.image.title", getTarballName()));
		} catch (final IOException | DigestException e) {
			throw new DockerApiException(e);
		}
	}

	private static File verify(final File file2) {
		final String fn = file2.getName();
		if (!fn.endsWith(".tar")) {
			throw new DockerApiException("OciHelm file must be a .tar file.");
		}
		return file2;
	}

	@Override
	public void copyTo(final Registry reg, final String string) {
		final ContentDescriptorTemplate layer = omt.getLayers().getFirst();
		try (InputStream fis = new FileInputStream(file);
				InputStream is = packStream(fis, file)) {
			reg.pushBlob(is, layer.getDigest());
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
		reg.pushConfig(config.getBytes());
		reg.pushManifest(omt, chart.getVersion());
	}

	private static InputStream packStream(final InputStream fis, final File file) throws IOException {
		final String fn = file.getName();
		if (fn.endsWith(".tgz") || fn.endsWith(".tar.gz")) {
			return fis;
		}
		return new GzipCompressingInputStream(fis);
	}

	private String getTarballName() {
		return "%s-%s.tgz".formatted(chart.getName(), chart.getVersion());
	}

	/**
	 * Return shortest line ending with `Chart.yaml`.
	 *
	 * @return A List of TarArchiveEntry.
	 */
	private String pickRootChart() {
		return aa.search("Chart.yaml")
				.stream()
				.sorted(Comparator.comparingInt(String::length))
				.toList()
				.getFirst();
	}

	private static String toSha256(final File file) {
		try (FileInputStream fis = new FileInputStream(file);
				InputStream in = packStream(fis, file)) {
			return toSha256Internal(in);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static String toSha256(final String jsonString) {
		try (ByteArrayInputStream in = new ByteArrayInputStream(jsonString.getBytes())) {
			return toSha256Internal(in);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static String toSha256Internal(final InputStream is) {
		try (DigestInputStream inSha256 = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"));
				OutputStream os = OutputStream.nullOutputStream()) {
			inSha256.transferTo(os);
			return bytesToHex(inSha256.getMessageDigest().digest());
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new DockerApiException(e);
		}
	}

	private String chartToJsonString() {
		try {
			return json.writeValueAsString(chart);
		} catch (final JsonProcessingException e) {
			throw new DockerApiException(e);
		}
	}

	private Chart loadChart(final String entry) {
		try (InputStream is = aa.getInputStream(entry)) {
			return yaml.readValue(is, Chart.class);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static String bytesToHex(final byte[] hash) {
		return IntStream.range(0, hash.length)
				.mapToObj(x -> hash[x])
				.map(b -> String.format("%02X", b))
				.collect(Collectors.joining())
				.toLowerCase();
	}

}
