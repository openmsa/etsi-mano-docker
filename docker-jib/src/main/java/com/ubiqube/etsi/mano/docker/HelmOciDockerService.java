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
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class HelmOciDockerService implements HelmService {

	@Override
	public void sendToRegistry(final InputStream is, final String filename, final RegistryInformations registry, final String imageName, final String tag) {
		try (final TemporaryFileSentry ts = new TemporaryFileSentry(".tar");
				OutputStream os = new FileOutputStream(ts.get().toString());
				InputStream ris = unpack(is, filename)) {
			ris.transferTo(os);
			send(ts.get(), registry, imageName, tag);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static void send(final Path path, final RegistryInformations registry, final String imageName, final String tag) {
		final Registry reg = Registry.of(registry, imageName);
		final OciHelmTarFile ohtf = new OciHelmTarFile(path.toFile());
		ohtf.copyTo(reg, tag);
	}

	private static InputStream unpack(final InputStream is, final String filename) throws IOException {
		if (filename.endsWith(".bz2")) {
			return new BZip2CompressorInputStream(is);
		}
		if (filename.endsWith(".tgz") || filename.endsWith(".tar.gz")) {
			return new GZIPInputStream(is);
		}
		return is;
	}

	@Override
	public void verifyConnection(final RegistryInformations registry) {
		Registry.of(registry, "dummy");
	}

	@Override
	public String getConnectionType() {
		return "OCI";
	}

}
