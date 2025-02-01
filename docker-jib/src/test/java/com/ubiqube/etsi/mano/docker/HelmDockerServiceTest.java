package com.ubiqube.etsi.mano.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

class HelmDockerServiceTest {
	/** Logger. */
	private static final Logger LOG = LoggerFactory.getLogger(HelmDockerServiceTest.class);

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

	private HelmDockerService helmDockerService;

	@BeforeEach
	void setUp() throws Exception {
		this.helmDockerService = new HelmDockerService();
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
		RegistryInformations reg = RegistryInformations.builder()
				.server("http://nexus.ubiqube.com/repository/helm-local/")
				.username("jenkins")
				.password("ubiqube")
				.build();
		helmDockerService.verifyConnection(reg);
		assertEquals("HELM", helmDockerService.getConnectionType());
	}

	@Test
	void testSendToRegistryTgz() throws FileNotFoundException {
		File tgz = new File(tmpDir, "test.tgz");
		FileInputStream fis = new FileInputStream(tgz);
		RegistryInformations reg = createRegInfo();
		// This one works only one time.
		assertThrows(DockerApiException.class, () -> helmDockerService.sendToRegistry(fis, "filename.tgz", reg, "imageName", "1.0.0"));
	}

	@Test
	void testSendToRegistryTarGz() throws FileNotFoundException {
		File tgz = new File(tmpDir, "test.tar.gz");
		FileInputStream fis = new FileInputStream(tgz);
		RegistryInformations reg = createRegInfo();
		assertThrows(DockerApiException.class, () -> helmDockerService.sendToRegistry(fis, "filename.tar.gz", reg, "imageName", "1.0.0"));
	}

	@Test
	void testSendToRegistryTar() throws FileNotFoundException {
		File tgz = new File(tmpDir, "test.tar");
		FileInputStream fis = new FileInputStream(tgz);
		RegistryInformations reg = createRegInfo();
		assertThrows(DockerApiException.class, () -> helmDockerService.sendToRegistry(fis, "filename.tar", reg, "imageName", "1.0.0"));
	}

	@Test
	void testSendToRegistryBz2() throws FileNotFoundException {
		File tgz = new File(tmpDir, "test.tar.bz2");
		FileInputStream fis = new FileInputStream(tgz);
		RegistryInformations reg = createRegInfo();
		assertThrows(DockerApiException.class, () -> helmDockerService.sendToRegistry(fis, "filename.tar.bz2", reg, "imageName", null));
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
				.server("http://nexus.ubiqube.com/repository/helm-local/")
				.username(props.getProperty("mano.docker.username"))
				.password(props.getProperty("mano.docker.password"))
				.build();
	}
}
