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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.google.common.io.Files;

class OciHelmTarFileTest {
	@TempDir
	private File tempDir;

	@ParameterizedTest
	@ValueSource(strings = { "test.tar.gz", "test.tgz" })
	void test(final String archName) {
		final TarArchiveEntry te1 = new TarArchiveEntry(new File("src/test/resources/Chart.yaml"), "test/folder/Chart.yaml");
		createTarFile(new File(tempDir, archName), te1);
		final File file = new File(tempDir, archName);
		assertThrows(DockerApiException.class, () -> new OciHelmTarFile(file));
	}

	@ParameterizedTest
	@ValueSource(strings = "test.tar")
	void testGood(final String archName) {
		final TarArchiveEntry te1 = new TarArchiveEntry(new File("src/test/resources/Chart.yaml"), "test/folder/Chart.yaml");
		createTarFile(new File(tempDir, archName), te1);
		final OciHelmTarFile srv = new OciHelmTarFile(new File(tempDir, archName));
		assertNotNull(srv);
//		final RegistryInformations regInfo = new RegistryInformations("http://nexus.ubiqube.com/local-docker/", "ovi", "00539fce-2cb7-11ef-b162-c8f750509d3b");
		final Registry reg = Mockito.mock(Registry.class);
		// new Registry(regInfo, "reg");
		srv.copyTo(reg, "tag");
	}

	static void createTarFile(final File file, final TarArchiveEntry... archiveEntries) {
		try (OutputStream fos = new FileOutputStream(file);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(fos)) {
			for (final TarArchiveEntry tarArchiveEntry : archiveEntries) {
				taos.putArchiveEntry(tarArchiveEntry);
				Files.copy(tarArchiveEntry.getFile(), taos);
				taos.closeArchiveEntry();
			}
			taos.finish();
		} catch (final IOException e) {
			throw new DockerApiException(e);
		}
	}
}
