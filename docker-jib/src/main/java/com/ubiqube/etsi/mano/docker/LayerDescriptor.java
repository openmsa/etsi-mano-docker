package com.ubiqube.etsi.mano.docker;

import com.google.cloud.tools.jib.api.DescriptorDigest;

public record LayerDescriptor(String blob, DescriptorDigest digest) {
	//
}
