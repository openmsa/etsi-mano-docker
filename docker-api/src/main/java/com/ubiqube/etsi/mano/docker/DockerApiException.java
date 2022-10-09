package com.ubiqube.etsi.mano.docker;

public class DockerApiException extends RuntimeException {
	/** Serial. */
	private static final long serialVersionUID = 1L;

	public DockerApiException(final Throwable e) {
		super(e);
	}

	public DockerApiException(final String string) {
		super(string);
	}
}
