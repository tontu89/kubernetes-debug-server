package io.github.tontu89.debugclientagent.utils.http.parser;

import io.github.tontu89.debugclientagent.utils.http.RawHttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Http Request Parser
 * @author citestra
 *
 */
@Slf4j
public class HttpRequestParser {

	/**
	 * Buffer size
	 */
	private static final int BUFFER_SIZE = 1024;
	
	/**
	 * Parse request
	 * @param reader request reader
	 * @throws IOException
	 */
	public static RawHttpRequest parseRequest(BufferedReader reader) throws IOException {

		RawHttpRequest rawHttpRequest = new RawHttpRequest();
		
		// REQUEST LINE
		setRequestLine(rawHttpRequest, reader);

		// HEADER
		setHeaders(rawHttpRequest, reader);

		// BODY
		setBody(rawHttpRequest, reader);

		rawHttpRequest.interpretRawUri();
		
		return rawHttpRequest;
	}

	/**
	 * Set request line
	 * @param reader
	 * @throws IOException
	 */
	private static void setRequestLine(RawHttpRequest rawHttpRequest, BufferedReader reader) throws IOException {
		String requestLine = reader.readLine();
		if (requestLine == null || requestLine.length() == 0 || requestLine.split(" ").length != 3) {
			throw new InvalidObjectException("Invalid Request-Line: " + requestLine);
		}
		String[] requestLineParts = requestLine.split(" ");
		rawHttpRequest.setRequestLine(requestLine);
		rawHttpRequest.setCommand(requestLineParts[0]);
		rawHttpRequest.setRawUri(requestLineParts[1]);
		rawHttpRequest.setHttpVersion(requestLineParts[2]);

		log.info("DebugAgent: rawUri {}", rawHttpRequest.getRawUri());
	}

	/**
	 * Set headers
	 * @param reader
	 * @throws IOException
	 */
	private static void setHeaders(RawHttpRequest rawHttpRequest, BufferedReader reader) throws IOException {
		String header = reader.readLine();
		while (header != null && header.length() > 0) {
			log.info("DebugAgent: original Header {}", header);
			rawHttpRequest.appendHeaderParameter(header);
			header = reader.readLine();
		}
	}

	/**
	 * Set body
	 * @param reader
	 * @throws IOException
	 */
	private static void setBody(RawHttpRequest rawHttpRequest, BufferedReader reader) throws IOException {
		char[] bodyChunk = new char[BUFFER_SIZE];
		int read;
		int timeOutMs = 60 * 1000;

		AtomicBoolean appendNewLineOnMessageBody = new AtomicBoolean(true);
		AtomicInteger contentLength = new AtomicInteger(-1);

		rawHttpRequest.getHeaders().forEach((key, value) -> {
			if ("content-length".equals(key.toLowerCase(Locale.ROOT))) {
				contentLength.set(Integer.parseInt(value));
			} else if ("content-type".equals(key.toLowerCase(Locale.ROOT)) && "application/json".equals(value.toLowerCase(Locale.ROOT))) {
				appendNewLineOnMessageBody.set(false);
			}
		});

		long reachTimeOutMs = System.currentTimeMillis() + timeOutMs;

		do {
			while (reader.ready() && (read = reader.read(bodyChunk, 0, BUFFER_SIZE)) != -1) {
				rawHttpRequest.appendMessageBody(new String(bodyChunk, 0, read), appendNewLineOnMessageBody.get());
			}
		} while (contentLength.get() != -1 && rawHttpRequest.getBody().length() < contentLength.get() && System.currentTimeMillis() < reachTimeOutMs);

		StringBuffer body = rawHttpRequest.getBody();
		if (body != null && body.length() > 2) {
			if ("\r\n".equals(body.subSequence(body.length() - 2, body.length()))) {
				body.delete(body.length() - 2, body.length());
			}
		}
	}

}
