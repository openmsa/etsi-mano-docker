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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.compress.utils.CountingInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryClient.Factory;

public class Registry {
	private static final Logger LOG = LoggerFactory.getLogger(Registry.class);
	private final EventHandlers evh;
	private final RegistryClient client;
	private final FailoverHttpClient fhc;
	private final RegistryInformations reg;
	private final String imageName;

	public Registry(final RegistryInformations registry, final String imageName) {
		this.imageName = imageName;
		this.reg = registry;
		this.evh = EventHandlers.builder().build();
		this.fhc = new FailoverHttpClient(true, true, evh::dispatch);
		String host = getHost(registry.getServer());
		final Factory factory = RegistryClient.factory(evh, host, imageName, fhc);
		final Credential cred = Credential.from(registry.getUsername(), registry.getPassword());
		factory.setCredential(cred);
		this.client = factory.newRegistryClient();
		try {
			client.doPushBearerAuth();
		} catch (IOException | RegistryException e) {
			throw new DockerApiException(e);
		}
	}

	private String getHost(String server) {
		URI url = URI.create(server);
		return url.getHost();
	}

	public static Registry of(final RegistryInformations registry, final String imageName) {
		return new Registry(registry, imageName);
	}

	public long pushBlob(final InputStream blobFile, final DescriptorDigest descriptorDigest) {
		Optional<BlobDescriptor> res;
		try {
			res = client.checkBlob(descriptorDigest);
		} catch (IOException | RegistryException e) {
			throw new DockerApiException(e);
		}
		if (res.isPresent()) {
			LOG.debug("Skipping layer : {}", descriptorDigest.getHash());
			return res.get().getSize();
		}
		return pushBlob2(blobFile, descriptorDigest);
	}

	private long pushBlob2(final InputStream is, final DescriptorDigest descriptorDigest) {
		try (final ProgressEventDispatcher progressEventDispatcher = ProgressEventDispatcher.newRoot(evh, "descr", 0);
				final ThrottledAccumulatingConsumer throttledProgressReporter = new ThrottledAccumulatingConsumer(progressEventDispatcher::dispatchProgress);) {
			final CountingInputStream cis = new CountingInputStream(is);
			final Blob blob = Blobs.from(cis);
			client.pushBlob(descriptorDigest, blob, null, throttledProgressReporter);
			return cis.getBytesRead();
		} catch (IOException | RegistryException e) {
			throw new DockerApiException(e);
		}
	}

	public DescriptorDigest pushConfig(final byte[] configRaw) {
		try (final ProgressEventDispatcher progressEventDispatcher = ProgressEventDispatcher.newRoot(evh, "config", 0);
				final ThrottledAccumulatingConsumer throttledProgressReporter = new ThrottledAccumulatingConsumer(progressEventDispatcher::dispatchProgress)) {
			final Blob configBlob = Blobs.from(new String(configRaw));
			final DescriptorDigest manifestDigest = DescriptorDigest.fromHash(buildSha256(configRaw));
			client.pushBlob(manifestDigest, configBlob, null, throttledProgressReporter);
			return manifestDigest;
		} catch (DigestException | NoSuchAlgorithmException | IOException | RegistryException e) {
			throw new DockerApiException(e);
		}
	}

	public ManifestAndDigest<ManifestTemplate> manifestPuller(final String tag) {
		try {
			return client.pullManifest(tag);
		} catch (IOException | RegistryException e) {
			LOG.info("Unable to pull manifest {}:{}", this.imageName, tag);
			LOG.trace("", e);
		}
		return null;
	}

	private static String buildSha256(final byte[] configRaw) throws NoSuchAlgorithmException {
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		final byte[] hash = digest.digest(configRaw);
		return IntStream.range(0, hash.length)
				.mapToObj(x -> hash[x])
				.map(b -> String.format("%02X", b))
				.collect(Collectors.joining())
				.toLowerCase();
	}

	public DescriptorDigest pushManifest(final BuildableManifestTemplate mft, final String tag) {
		try {
			return client.pushManifest(mft, tag);
		} catch (final RegistryException e) {
			LOG.trace("Could not upload " + tag, e);
			LOG.warn("Could not upload docker image {}", tag);
			return null;
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	public void deleteManifest(final String name) {
		Optional<ManifestAndDigest<ManifestTemplate>> res;
		try {
			res = client.checkManifest(name);
		} catch (IOException | RegistryException e) {
			throw new DockerApiException(e);
		}
		if (res.isEmpty()) {
			return;
		}
		URL url;
		final String apiRouteBase = "https://" + reg.getServer() + "/v2/";
		try {
			url = new URL(apiRouteBase + imageName + "/manifests/" + res.get().getDigest().getHash());
			final Response cr = fhc.call("DELETE", url, null);
			if (cr.getStatusCode() >= 400) {
				throw new DockerApiException("Bad error Code :" + cr.getStatusCode());
			}
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}

	}

}
