package io.github.tontu89.debugclientagent.utils.http.parser;

import io.github.tontu89.debugclientagent.utils.http.CompressionUtils;
import io.github.tontu89.debugclientagent.utils.http.RawHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.DataFormatException;

/**
 * Http Response parser
 * @author citestra
 *
 */
public class HttpResponseParser {

	/**
	 * Buffer size
	 */
	private final static int BUFFER_SIZE = 65536;
	
	/**
	 * Parse response from UrlConnection
	 * @return Http response
	 * @throws IOException 
	 * @throws DataFormatException 
	 */
	public static RawHttpResponse parseResponse(HttpURLConnection connection) throws IOException, DataFormatException {
		RawHttpResponse rawHttpResponse = new RawHttpResponse();
		
		// Get the response stream
		InputStream serverToProxyStream = null;
		int responseCode = connection.getResponseCode();
		if (responseCode >= 400) {
			serverToProxyStream = connection.getErrorStream();
		} else {
			serverToProxyStream = connection.getInputStream();
		}
		
		if (serverToProxyStream != null) {
			// Status line
			rawHttpResponse.setStatusLine(connection.getHeaderField(0));
			rawHttpResponse.setResponseCode(connection.getResponseCode());
			
			// Headers
			rawHttpResponse.setHeaders(connection.getHeaderFields());
			
			// Read body
			String contentEncoding = connection.getContentEncoding();
			rawHttpResponse.setContentEncoding(contentEncoding);
			
			ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
			byte by[] = new byte[ BUFFER_SIZE ];
			int index = serverToProxyStream.read(by, 0, BUFFER_SIZE);
			while ( index != -1 ) {
				responseBuffer.write(by, 0, index);
				index = serverToProxyStream.read( by, 0, BUFFER_SIZE );
			}
			responseBuffer.flush();

			// Decode body
			byte[] responsePlain = CompressionUtils.decodeContentBody(responseBuffer.toByteArray(), contentEncoding);
			rawHttpResponse.setRawResponseBody(responsePlain);
			
			// Close Remote Server -> Proxy Stream
			serverToProxyStream.close();
		}
		return rawHttpResponse;
	}
}
