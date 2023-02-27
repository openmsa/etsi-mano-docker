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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.ubiqube.etsi.mano.docker;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TemporaryFileSentry implements Closeable {
	private final Path file;

	public TemporaryFileSentry() {
		try {
			file = Files.createTempFile(Paths.get("/tmp/"), "mano", "vnfm");
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	public Path get() {
		return file;
	}

	@Override
	public void close() {
		try {
			Files.delete(file);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}
}
