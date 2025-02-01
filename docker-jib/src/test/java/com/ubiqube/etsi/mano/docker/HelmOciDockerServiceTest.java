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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HelmOciDockerServiceTest {
	/** Logger. */
	private static final Logger LOG = LoggerFactory.getLogger(HelmOciDockerServiceTest.class);
	private static final Properties props = new Properties();
	private static final String CHART_YAML = """
			apiVersion: v2
			name: mano-ha
			description: Ubiqube ETSI-MANO server in HA mode.
			version: 0.0.1
			keywords:
			  - mano-ha
			  - ETSI MANO
			  - VNFM
			  - NFVO
			sources:
			  - https://github.com/ubiqube/etsi-mano-docker.git
			home: http://ubiqube.com/
			maintainers:
			  - name: mano-team
			    email: mano-team@ubiqube.com
			    url: http://ubiqube.com/
			appVersion: 3.0.0
			kubeVersion: ">= 1.30.0"
			type: application
							""";
	@TempDir
	private File tmpDir;

	private HelmOciDockerService helmOciDockerService;

	@BeforeEach
	void setUp() throws Exception {
		this.helmOciDockerService = new HelmOciDockerService();
		OutputStream outputStream = new GZIPOutputStream(new FileOutputStream(new File(tmpDir, "test.tar.gz")));
		createFile(outputStream);
		//
		OutputStream outputStream2 = new GZIPOutputStream(new FileOutputStream(new File(tmpDir, "test.tgz")));
		createFile(outputStream2);
		//
		OutputStream outputStream3 = new BZip2CompressorOutputStream(new FileOutputStream(new File(tmpDir, "test.tar.bz2")));
		createFile(outputStream3);
		//
		OutputStream outputStream4 = new FileOutputStream(new File(tmpDir, "test.tar"));
		createFile(outputStream4);
	}

	void createFile(final OutputStream outputStream) throws IOException {
		TarArchiveOutputStream tos = new TarArchiveOutputStream(outputStream);
		ByteArrayInputStream bis = new ByteArrayInputStream(CHART_YAML.getBytes());
		TarArchiveEntry te = new TarArchiveEntry(Paths.get("/Chart.yaml").toFile(), "Chart.yaml");
		te.setSize(CHART_YAML.length());
		tos.putArchiveEntry(te);
		bis.transferTo(tos);
		tos.closeArchiveEntry();
		tos.close();
	}

	@Test
	void test() {
		RegistryInformations conn = createRegInfo();
		helmOciDockerService.verifyConnection(conn);
		helmOciDockerService.getConnectionType();
		assertTrue(true);
	}

	@Test
	void testDockerContainerTar() throws Exception {
		File tgz = new File(tmpDir, "test.tar");
		FileInputStream fis = new FileInputStream(tgz);
		helmOciDockerService.sendToRegistry(fis, tgz.getName(), createRegInfo(), "mano-ci/test", "latest");
		assertTrue(true);
	}

	@Test
	void testDockerContainerTgz() throws Exception {
		File tgz = new File(tmpDir, "test.tgz");
		FileInputStream fis = new FileInputStream(tgz);
		helmOciDockerService.sendToRegistry(fis, tgz.getName(), createRegInfo(), "mano-ci/test", "latest");
		assertTrue(true);
	}

	@Test
	void testDockerContainerTargz() throws Exception {
		File tgz = new File(tmpDir, "test.tar.gz");
		FileInputStream fis = new FileInputStream(tgz);
		helmOciDockerService.sendToRegistry(fis, tgz.getName(), createRegInfo(), "mano-ci/test", "latest");
		assertTrue(true);
	}

	@Test
	void testDockerContainerBz2() throws Exception {
		File tgz = new File(tmpDir, "test.tar.bz2");
		FileInputStream fis = new FileInputStream(tgz);
		helmOciDockerService.sendToRegistry(fis, tgz.getName(), createRegInfo(), "mano-ci/test", "latest");
		assertTrue(true);
	}

	private static RegistryInformations createRegInfo() {
		final String configPath = System.getenv().get("CONFIGURATION_FILE");
		LOG.info("Using file: {}", configPath);
		try (FileInputStream fis = new FileInputStream(configPath)) {
			props.load(fis);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
		return RegistryInformations.builder()
				.server(props.getProperty("mano.docker.url"))
				.username(props.getProperty("mano.docker.username"))
				.password(props.getProperty("mano.docker.password"))
				.build();
	}
}
