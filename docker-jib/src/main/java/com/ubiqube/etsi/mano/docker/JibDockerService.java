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
