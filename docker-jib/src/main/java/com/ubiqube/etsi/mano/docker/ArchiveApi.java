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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;

public class ArchiveApi {

	private final TarFile tf;

	public ArchiveApi(final TarFile tf) {
		this.tf = tf;
	}

	public InputStream getInputStream(final String path) {
		final TarArchiveEntry index = findEntry(path).orElseThrow();
		try {
			return tf.getInputStream(index);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private Optional<TarArchiveEntry> findEntry(final String entry) {
		return tf.getEntries()
				.stream()
				.filter(x -> entry.equals(x.getName()))
				.findFirst();
	}

	public byte[] getContent(final String path) {
		try (InputStream is = getInputStream(path);
				ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			is.transferTo(baos);
			return baos.toByteArray();
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}

	}

}
