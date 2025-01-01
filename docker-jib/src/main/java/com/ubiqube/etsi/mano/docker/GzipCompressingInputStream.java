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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;

/**
 * @author mwyraz
 *
 *         Wraps an input stream and compresses it's contents. Similiar to
 *         DeflateInputStream but adds GZIP-header and trailer See
 *         GzipOutputStream for details. LICENSE: Free to use. Contains some
 *         lines from GzipOutputStream, so oracle's license might apply as well!
 *         https://stackoverflow.com/questions/11036280/compress-an-inputstream-with-gzip
 */
public class GzipCompressingInputStream extends SequenceInputStream {
	public GzipCompressingInputStream(final InputStream in) {
		this(in, 512);
	}

	public GzipCompressingInputStream(final InputStream in, final int bufferSize) {
		super(new StatefullGzipStreamEnumerator(in, bufferSize));
	}

	enum StreamState {
		HEADER,
		CONTENT,
		TRAILER
	}

	protected static class StatefullGzipStreamEnumerator implements Enumeration<InputStream> {

		protected final InputStream in;
		protected final int bufferSize;
		protected StreamState state;

		public StatefullGzipStreamEnumerator(final InputStream in, final int bufferSize) {
			this.in = in;
			this.bufferSize = bufferSize;
			state = StreamState.HEADER;
		}

		@Override
		public boolean hasMoreElements() {
			return state != null;
		}

		@Override
		public InputStream nextElement() {
			return switch (state) {
			case HEADER -> {
				state = StreamState.CONTENT;
				yield createHeaderStream();
			}
			case CONTENT -> {
				state = StreamState.TRAILER;
				yield createContentStream();
			}
			case TRAILER -> {
				state = null;
				yield createTrailerStream();
			}
			};
		}

		static final int GZIP_MAGIC = 0x8b1f;
		static final byte[] GZIP_HEADER = {
				(byte) GZIP_MAGIC, // Magic number (short)
				(byte) (GZIP_MAGIC >> 8), // Magic number (short)
				Deflater.DEFLATED, // Compression method (CM)
				0, // Flags (FLG)
				0, // Modification time MTIME (int)
				0, // Modification time MTIME (int)
				0, // Modification time MTIME (int)
				0, // Modification time MTIME (int)
				0, // Extra flags (XFLG)
				0 // Operating system (OS)
		};

		@SuppressWarnings("static-method")
		protected InputStream createHeaderStream() {
			return new ByteArrayInputStream(GZIP_HEADER);
		}

		protected InternalGzipCompressingInputStream contentStream;

		protected InputStream createContentStream() {
			contentStream = new InternalGzipCompressingInputStream(new CRC32InputStream(in), bufferSize);
			return contentStream;
		}

		protected InputStream createTrailerStream() {
			return new ByteArrayInputStream(contentStream.createTrailer());
		}
	}

	/**
	 * Internal stream without header/trailer
	 */
	protected static class CRC32InputStream extends FilterInputStream {
		protected CRC32 crc = new CRC32();
		protected long byteCount;

		public CRC32InputStream(final InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			final int val = super.read();
			if (val >= 0) {
				crc.update(val);
				byteCount++;
			}
			return val;
		}

		@Override
		public int read(final byte[] b, final int off, final int lenIn) throws IOException {
			final int len = super.read(b, off, lenIn);
			if (len >= 0) {
				crc.update(b, off, len);
				byteCount += len;
			}
			return len;
		}

		public long getCrcValue() {
			return crc.getValue();
		}

		public long getByteCount() {
			return byteCount;
		}
	}

	/**
	 * Internal stream without header/trailer
	 */
	protected static class InternalGzipCompressingInputStream extends DeflaterInputStream {
		protected final CRC32InputStream crcIn;

		public InternalGzipCompressingInputStream(final CRC32InputStream in, final int bufferSize) {
			super(in, new Deflater(Deflater.DEFAULT_COMPRESSION, true), bufferSize);
			crcIn = in;
		}

		@Override
		public void close() throws IOException {
			if (in != null) {
				try {
					def.end();
					in.close();
				} finally {
					in = null;
				}
			}
		}

		protected static final int TRAILER_SIZE = 8;

		public byte[] createTrailer() {
			final byte[] trailer = new byte[TRAILER_SIZE];
			writeTrailer(trailer, 0);
			return trailer;
		}

		/*
		 * Writes GZIP member trailer to a byte array, starting at a given offset.
		 */
		private void writeTrailer(final byte[] bufIn, final int offset) {
			writeInt((int) crcIn.getCrcValue(), bufIn, offset); // CRC-32 of uncompr. data
			writeInt((int) crcIn.getByteCount(), bufIn, offset + 4); // Number of uncompr. bytes
		}

		/*
		 * Writes integer in Intel byte order to a byte array, starting at a given
		 * offset.
		 */
		private void writeInt(final int i, final byte[] bufIn, final int offset) {
			writeShort(i & 0xffff, bufIn, offset);
			writeShort((i >> 16) & 0xffff, bufIn, offset + 2);
		}

		/*
		 * Writes short integer in Intel byte order to a byte array, starting at a given
		 * offset
		 */
		@SuppressWarnings("static-method")
		private void writeShort(final int s, final byte[] bufIn, final int offset) {
			bufIn[offset] = (byte) (s & 0xff);
			bufIn[offset + 1] = (byte) ((s >> 8) & 0xff);
		}
	}

}
