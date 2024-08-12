package com.ubiqube.etsi.mano.docker;

import java.io.InputStream;

public interface HelmService {

	void sendToRegistry(InputStream is, String filename, RegistryInformations registry, String imageName, final String tag);

	void verifyConnection(RegistryInformations registry);

	String getConnectionType();

}
