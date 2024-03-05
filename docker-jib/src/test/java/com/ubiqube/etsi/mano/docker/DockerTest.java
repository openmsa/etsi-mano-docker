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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;

@SuppressWarnings("static-method")
class DockerTest {
	private static final Logger LOG = LoggerFactory.getLogger(DockerTest.class);
	private static final String DOCKER_IMAGE = "http://nexus.ubiqube.com/repository/raw/ci-artefacts/spring-petclinic-docker.tar";
	private static final String OCI_IMAGE = "http://nexus.ubiqube.com/repository/raw/ci-artefacts/spring-petclinic-oci.tar";

	@TempDir
	private File tmpDir;
	final static Properties props = new Properties();
	private final JibDockerService dockerService;

	static Stream<Arguments> arguments = Stream.of(
			Arguments.of(DOCKER_IMAGE, "mano-ci/docker-junit"),
			Arguments.of(OCI_IMAGE, "mano-ci/oci-junit"),
			Arguments.of(DOCKER_IMAGE, "mano-ci/docker-junit"),
			Arguments.of(OCI_IMAGE, "mano-ci/oci-junit"));

	public DockerTest() {
		this.dockerService = new JibDockerService();
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

	@BeforeAll
	public static void beforeAll() {
		new File("/tmp/oci-images/").mkdirs();
		arguments.forEach(x -> {
			final RegistryInformations registry = createRegInfo();
			final JibDockerService ds = new JibDockerService();
			deleteImage(registry, (String) x.get()[1]);
		});
	}

	private static void deleteImage(final RegistryInformations registry, final String imageName) {
		final Registry reg = Registry.of(registry, imageName);
		final ManifestAndDigest<ManifestTemplate> mf = reg.manifestPuller("latest");
		final String baseApi = "https://" + registry.getServer() + "/v2/";
		if (null == mf) {
			LOG.info("Could not remove {}", imageName);
			return;
		}
		final Object resp = RegistryQaClient.delete(registry, baseApi + imageName + "/manifests/" + mf.getDigest().toString());
		System.out.println("" + resp);
		if (OciManifestTemplate.MANIFEST_MEDIA_TYPE.equals(mf.getManifest().getManifestMediaType())) {
			final OciManifestTemplate ociMf = (OciManifestTemplate) mf.getManifest();
			ociMf.getLayers().forEach(x -> {
				final Object delLayer = RegistryQaClient.delete(registry, baseApi + imageName + imageName + "/blobs/" + x.getDigest());
				System.out.println(delLayer);
			});
		} else {
			final V22ManifestTemplate vmf = (V22ManifestTemplate) mf.getManifest();
			vmf.getLayers().forEach(x -> {
				final Object delLayer = RegistryQaClient.delete(registry, baseApi + imageName + imageName + "/blobs/" + x.getDigest());
				System.out.println(delLayer);
			});
			System.out.println("DOCKER");
		}
	}

	private File download(final String dockerImage) throws MalformedURLException {
		final URL url = new URL(dockerImage);
		final String fName = new File(url.getFile()).getName();
		final File target = new File(tmpDir, fName);
		if (target.exists()) {
			LOG.debug("Skipping {}", fName);
			return target;
		}
		try (InputStream is = url.openStream();
				OutputStream os = new FileOutputStream(target)) {
			LOG.debug("Downloading {}", fName);
			is.transferTo(os);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return target;
	}

	static Stream<Arguments> testDockerContainer() {
		return Stream.of(
				Arguments.of(DOCKER_IMAGE, "mano-ci/docker-junit"),
				Arguments.of(OCI_IMAGE, "mano-ci/oci-junit"));
	}

	@ParameterizedTest
	@MethodSource
	void testDockerContainer(final String url, final String imageName) throws Exception {
		final File tgt = download(url);
		try (InputStream is = new FileInputStream(tgt)) {
			final RegistryInformations registry = createRegInfo();
			dockerService.sendToRegistry(is, registry, imageName, "latest");
		}
		assertTrue(true);
	}
}
