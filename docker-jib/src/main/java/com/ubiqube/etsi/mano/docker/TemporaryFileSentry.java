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
