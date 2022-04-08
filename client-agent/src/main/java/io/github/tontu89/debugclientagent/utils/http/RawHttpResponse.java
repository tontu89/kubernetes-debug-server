package io.github.tontu89.debugclientagent.utils.http;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.StatusLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Http Response
 * @author citestra
 *
 */
public class RawHttpResponse {

	private static final int BUFFER_SIZE = 65536;

	/**
	 * Status line
	 */
	private String statusLine;
	
	/**
	 * response code
	 */
	private int responseCode;
	
	/**
	 * Headers
	 */
	private Map<String, String> headers;
	
	/**
	 * Raw response body
	 */
	private byte[] rawResponseBody;
	
	/**
	 * Decoded response body
	 */
	private String plainResponseBody;
	
	/**
	 * Encoded response body
	 */
	private byte[] encodedResponseBody;

	/**
	 * Encoding algorithm
	 */
	private String contentEncoding;
	
	/**
	 * 
	 * @return is HttpResponse empty ?
	 */
	public boolean isNotBlank() {
		return this.statusLine != null && !this.statusLine.isEmpty() && this.headers!= null && !this.headers.isEmpty();
	}
	
    /**
     * Tamper with headers
     * @param tamperedHeaders
     */
    public void tamperWithHeaders(Map<String, String> tamperedHeaders, List<String> immutableHeaders) {
    	if (tamperedHeaders != null) {
    		tamperedHeaders.entrySet().stream().forEach((entry) -> {
    			if (!immutableHeaders.contains(entry.getKey())) {
    				this.headers.put(entry.getKey(), entry.getValue());
    			}
    		});
    	}
    }
    
	// ******************************* GETTERS / SETTERS ******************************************
	public String getStatusLine() {
		return statusLine;
	}

	public void setStatusLine(String statusLine) {
		if (statusLine != null) {
			this.statusLine = statusLine.endsWith("\r\n") ? statusLine.substring(0, statusLine.length() - 2) : statusLine;
		}
	}

	public void setStatusLine(StatusLine statusLine) {
		this.statusLine = String.format("%s %d %s", statusLine.getHttpVersion(), statusLine.getStatusCode(), HttpStatus.getStatusText(statusLine.getStatusCode()));
	}

	public int getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(int responseCode) {
		this.responseCode = responseCode;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaderFields(Map<String, String> headers) {
		this.headers = headers;
	}

	public void setHeaders(Map<String, List<String>> headers) {
		this.headers = new HashMap<>();
		if (headers != null) {
			headers.forEach((key, value) -> {
				if (key != null) {
					this.headers.put(key, String.join(", ", value));
				}
			});
		}
	}
	
	public byte[] retreiveRawResponseBody() {
		return rawResponseBody;
	}

	public void setRawResponseBody(byte[] rawResponseBody) throws IOException {
		this.rawResponseBody = rawResponseBody;
		this.plainResponseBody =  new String(rawResponseBody, StandardCharsets.UTF_8);
		this.encodedResponseBody = CompressionUtils.encodeContentBody(rawResponseBody, this.contentEncoding);
	}
	
	public String getPlainResponseBody() {
		return plainResponseBody;
	}

	public void setPlainResponseBody(String plainResponseBody) {
		this.plainResponseBody = plainResponseBody;
	}

	public byte[] retrieveEncodedResponseBody() throws IOException {
		return encodedResponseBody;
	}

	public void setEncodedResponseBody(byte[] encodedResponseBody) {
		this.encodedResponseBody = encodedResponseBody;
	}
	
	public String getContentEncoding() {
		return contentEncoding;
	}

	public void setContentEncoding(String contentEncoding) {
		this.contentEncoding = contentEncoding;
	}

	public void writeTo(OutputStream os) throws IOException {

		// Send status line
		os.write(String.format("%s\r\n", this.getStatusLine()).getBytes(StandardCharsets.UTF_8));

		if (this.getHeaders() != null) {
			// send headers (filtered)
			for (Map.Entry<String, String> header : this.getHeaders().entrySet()) {
				if ("content-length".equals(header.getKey().toLowerCase(Locale.ROOT))) {
					continue;
				}
				os.write(
						new StringBuilder().append(header.getKey())
								.append(": ")
								.append(header.getValue())
								.append("\r\n")
								.toString()
								.getBytes(StandardCharsets.UTF_8));
			}
		}

		byte[] body = null;

		body = retrieveEncodedResponseBody();

		if (body == null && this.getPlainResponseBody() != null) {
			body = this.getPlainResponseBody().getBytes(StandardCharsets.UTF_8);
		}

		if (body != null) {
			os.write(("Content-Length: " + body.length + "\r\n").getBytes(StandardCharsets.UTF_8));
		} else {
			os.write(("Content-Length: 0\r\n").getBytes(StandardCharsets.UTF_8));
		}

		// end headers
		os.write("\r\n".getBytes(StandardCharsets.UTF_8));

		if (body != null) {
			// Send encoded stream to client (navigator)
			ByteArrayInputStream streamToSend = new ByteArrayInputStream(body);
			byte[] bodyChunk = new byte[BUFFER_SIZE];
			int read = streamToSend.read(bodyChunk, 0, BUFFER_SIZE);
			while (read != -1) {
				os.write(bodyChunk, 0, read);
				read = streamToSend.read(bodyChunk, 0, BUFFER_SIZE);
			}
		}

		os.flush();
	}
}
