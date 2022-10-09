package com.ubiqube.etsi.mano.docker;

import java.io.InputStream;

public interface DockerService {

	void sendToRegistry(InputStream is, RegistryInformations registry, String imageName, final String tag);
}
