package io.github.tontu89.debugserverlib.filter.requestwrapper;

import com.auth0.jwt.exceptions.JWTDecodeException;
import io.github.tontu89.debugserverlib.utils.HttpUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StreamUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.github.tontu89.debugserverlib.utils.Constants.LOG_ERROR_PREFIX;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private byte[] cachedBody;
    private Map<String, String> headers;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        InputStream requestInputStream = request.getInputStream();
        this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
        this.initHeader();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        // Create a reader from cachedContent
        // and return it
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream));
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    private void initHeader() {
        this.headers = new HashMap<>();
        Optional.ofNullable(this.getHeaderNames()).ifPresent(headerNames -> {
            while(headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = this.getHeader(headerName);
                this.headers.put(headerName, headerValue);
            }
        });
    }

}
