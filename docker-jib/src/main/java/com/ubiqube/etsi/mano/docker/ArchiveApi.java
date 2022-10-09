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
