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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

public class HelmDockerService implements HelmService {

	@Override
	public void sendToRegistry(final InputStream is, final String filename, final RegistryInformations registry, final String imageName, final String tag) {
		try (final TemporaryFileSentry ts = new TemporaryFileSentry(".tgz");
				OutputStream os = new FileOutputStream(ts.get().toString());
				InputStream ris = unpack(is, filename);
				OutputStream ros = pack(os, filename)) {
			ris.transferTo(ros);
			send(ts.get(), registry, imageName, tag);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}

	private static void send(final Path path, final RegistryInformations registry, final String imageName, final String tag) {
		final WebClient wc = createWebClient(registry);
		try (InputStream fis = new FileInputStream(path.toFile());) {
			final Resource resource = new InputStreamResource(fis);
			final URI uri = UriComponentsBuilder.fromHttpUrl(registry.getServer()).pathSegment("mano").path(buildImageName(imageName, tag)).build().toUri();
			final Mono<HttpStatusCode> res = wc
					.put()
					.uri(uri)
					.body(BodyInserters.fromResource(resource))
					.exchangeToMono(response -> {
						if (HttpStatus.OK.equals(response.statusCode())) {
							return response.bodyToMono(HttpStatus.class).thenReturn(response.statusCode());
						}
						throw new DockerApiException("Error uploading file");
					});
			res.block();
		} catch (final IOException e) {
			throw new DockerApiException("Error uploading file");
		}
	}

	private static WebClient createWebClient(final RegistryInformations registry) {
		return WebClient.builder()
				.baseUrl(registry.getServer())
				.filter(ExchangeFilterFunctions.basicAuthentication(registry.getUsername(), registry.getPassword()))
				.build();
	}

	private static String buildImageName(final String imageName, final String tag) {
		return "%s-%s.tgz".formatted(imageName, Optional.ofNullable(tag).orElseGet(() -> "latest"));
	}

	private static OutputStream pack(final OutputStream os, final String filename) throws IOException {
		if (filename.endsWith(".tgz") || filename.endsWith(".tar.gz")) {
			return os;
		}
		return new GZIPOutputStream(os);
	}

	private static InputStream unpack(final InputStream is, final String filename) throws IOException {
		if (filename.endsWith(".bz2")) {
			return new BZip2CompressorInputStream(is);
		}
		return is;
	}

	@Override
	public void verifyConnection(final RegistryInformations registry) {
		final URI uri = UriComponentsBuilder.fromHttpUrl(registry.getServer()).path("index.yaml").build().toUri();
		final WebClient c = createWebClient(registry);
		c.get()
				.uri(uri)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(new DockerException("" + r.statusCode())))
				.toBodilessEntity()
				.block();
	}

	@Override
	public String getConnectionType() {
		return "HELM";
	}

}
