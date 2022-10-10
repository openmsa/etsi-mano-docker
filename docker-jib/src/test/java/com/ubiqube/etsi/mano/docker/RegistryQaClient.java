/**
 *     Copyright (C) 2019-2020 Ubiqube.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BufferedHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistryQaClient {
	private static final Logger LOG = LoggerFactory.getLogger(RegistryQaClient.class);
	private static final String RESPONSE_DIGEST_HEADER = "Docker-Content-Digest";
	public static final String MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
	public static final String MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

	static RegistryResponse get(final String url) {
		final HttpGet httpGet = new HttpGet(url);
		httpGet.addHeader(new BasicHeader("Accept", "application/vnd.docker.distribution.manifest.v2+json"));
		return execute(httpGet);
	}

	static RegistryResponse delete(final RegistryInformations registry, final String url) {
		final HttpDelete del = new HttpDelete(url);
		final String auth = registry.getUsername() + ":" + registry.getPassword();
		del.addHeader(new BasicHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.getBytes())));
		return execute(del);
	}

	private static RegistryResponse execute(final HttpRequestBase req) {
		LOG.debug("{}", req);
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			try (CloseableHttpResponse response = httpclient.execute(req)) {
				final HttpEntity entity = response.getEntity();
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (InputStream es = entity.getContent()) {
					es.transferTo(baos);
				}
				final String hash = extractHeader(response, RESPONSE_DIGEST_HEADER);
				return new RegistryResponse(response.getStatusLine().getStatusCode(), hash, new String(baos.toByteArray()));
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String extractHeader(final CloseableHttpResponse response, final String key) {
		final HeaderIterator hite = response.headerIterator(key);
		if (hite.hasNext()) {
			return ((BufferedHeader) hite.next()).getValue();
		}
		return null;
	}
}
