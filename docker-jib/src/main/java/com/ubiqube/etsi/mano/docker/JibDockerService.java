package com.ubiqube.etsi.mano.docker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.tar.TarFile;

public class JibDockerService implements DockerService {

	@Override
	public void sendToRegistry(final InputStream is, final RegistryInformations registry, final String imageName, final String tag) {
		try (final TemporaryFileSentry ts = new TemporaryFileSentry();
				OutputStream os = new FileOutputStream(ts.get().toString())) {
			is.transferTo(os);
			send(ts.get(), registry, imageName, tag);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static void send(final Path path, final RegistryInformations registry, final String imageName, final String tag) {
		try (final TarFile tf = new TarFile(path)) {
			final boolean oci = isOci(tf);
			ContainerTarFile tar;
			if (oci) {
				tar = new OciTarFile(tf);
			} else {
				tar = new DockerTarFile(tf);
			}
			final Registry reg = Registry.of(registry, imageName);
			tar.copyTo(reg, tag);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static boolean isOci(final TarFile tarFile) {
		return tarFile.getEntries()
				.stream()
				.anyMatch(x -> "oci-layout".equals(x.getName()));
	}

}
