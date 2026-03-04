package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoxLangDownloadService {

	private static final int BUFFER_SIZE = 8192;

	private BoxLangDownloadService() {
	}

	public static void downloadTo( URL url, Path target, ProgressIndicator indicator ) throws IOException {
		Files.createDirectories( target.getParent() );

		HttpURLConnection connection = ( HttpURLConnection ) url.openConnection();
		connection.setRequestMethod( "GET" );
		connection.setConnectTimeout( 30000 );
		connection.setReadTimeout( 60000 );

		try {
			int responseCode = connection.getResponseCode();
			if ( responseCode != HttpURLConnection.HTTP_OK ) {
				throw new IOException( "HTTP error " + responseCode + " downloading " + url );
			}

			long contentLength = connection.getContentLengthLong();

			if ( indicator != null ) {
				if ( contentLength > 0 ) {
					indicator.setIndeterminate( false );
					indicator.setFraction( 0.0 );
				} else {
					indicator.setIndeterminate( true );
				}
			}

			try ( InputStream input = connection.getInputStream();
			    OutputStream output = Files.newOutputStream( target ) ) {
				byte[]	buffer	= new byte[ BUFFER_SIZE ];
				long	total	= 0;
				int		read;
				while ( ( read = input.read( buffer ) ) >= 0 ) {
					if ( indicator != null && indicator.isCanceled() ) {
						throw new IOException( "Download cancelled" );
					}
					output.write( buffer, 0, read );
					total += read;
					if ( indicator != null ) {
						if ( contentLength > 0 ) {
							double fraction = ( double ) total / contentLength;
							indicator.setFraction( fraction );
							indicator.setText2( String.format( "Downloaded %s of %s (%.0f%%)",
							    formatBytes( total ), formatBytes( contentLength ), fraction * 100 ) );
						} else {
							indicator.setText2( String.format( "Downloaded %s", formatBytes( total ) ) );
						}
					}
				}
			}
		} finally {
			connection.disconnect();
		}
	}

	private static String formatBytes( long bytes ) {
		if ( bytes < 1024 ) {
			return bytes + " B";
		} else if ( bytes < 1024 * 1024 ) {
			return String.format( "%.1f KB", bytes / 1024.0 );
		} else {
			return String.format( "%.1f MB", bytes / ( 1024.0 * 1024.0 ) );
		}
	}
}
