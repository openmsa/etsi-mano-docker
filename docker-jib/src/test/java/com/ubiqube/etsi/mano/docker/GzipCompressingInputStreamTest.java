/**
 * Copyright (C) 2019-2025 Ubiqube.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ubiqube.etsi.mano.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GzipCompressingInputStreamTest {
	@TempDir
	private File tempDir;

	@Test
	void test() {
		try (final InputStream is = getClass().getResourceAsStream("/logback.xml");
				InputStream gzis = new GzipCompressingInputStream(is);
				FileOutputStream fos = new FileOutputStream(new File(tempDir, "test.gz"))) {
			gzis.transferTo(fos);
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
		String sha256;
		try (FileInputStream fis = new FileInputStream(new File(tempDir, "test.gz"));
				DigestInputStream inSha256 = new DigestInputStream(fis, MessageDigest.getInstance("SHA-256"));
				OutputStream os = OutputStream.nullOutputStream()) {
			inSha256.transferTo(os);
			final MessageDigest md = inSha256.getMessageDigest();
			sha256 = bytesToHex(md.digest());
		} catch (final IOException | NoSuchAlgorithmException e) {
			throw new DockerApiException(e);
		}
		assertEquals("3bb888a7c5ca9dd2446d7f8c03ac62b87e62cd1d8c88766e69a6bf7531000f95", sha256);
	}

	private static String bytesToHex(final byte[] hash) {
		return IntStream.range(0, hash.length)
				.mapToObj(x -> hash[x])
				.map(b -> String.format("%02X", b))
				.collect(Collectors.joining())
				.toLowerCase();
	}

}
