package com.ubiqube.etsi.mano.docker;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RegistryInformations {

	private String server;

	private String username;

	private String password;
}
