package com.ortussolutions.intellijboxlang.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

public class ForgeBoxDownloadResolverTest {

	@Test
	public void buildsStorageEndpointWithEncodedVersion() {
		assertEquals(
		    "https://forgebox.io/api/v1/storage/bx-lsp/1.17.0%2B16",
		    ForgeBoxDownloadResolver.storageEndpoint( "bx-lsp", "1.17.0+16" ) );
	}

	@Test
	public void readsSignedDownloadUrlFromStorageResponse() throws IOException {
		assertEquals(
		    "https://storage.example.test/bx-lsp.zip?signature=abc",
		    ForgeBoxDownloadResolver.parseStorageLocation(
		        "{\"error\":false,\"data\":\"https://storage.example.test/bx-lsp.zip?signature=abc\"}" ) );
	}

	@Test
	public void rejectsStorageResponseWithoutDownloadUrl() {
		assertThrows( IOException.class, () -> ForgeBoxDownloadResolver.parseStorageLocation( "{\"error\":false,\"data\":null}" ) );
	}
}
