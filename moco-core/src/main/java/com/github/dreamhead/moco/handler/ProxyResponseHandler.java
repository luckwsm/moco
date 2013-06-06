package com.github.dreamhead.moco.handler;

import com.github.dreamhead.moco.MocoConfig;
import com.github.dreamhead.moco.ResponseHandler;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class ProxyResponseHandler implements ResponseHandler {
    private final URL url;

    public ProxyResponseHandler(URL url) {
        this.url = url;
    }

    @Override
    public void writeToResponse(HttpRequest request, HttpResponse response) {
        try {
            URL url = remoteUrl(request);

            DefaultHttpClient httpclient = new DefaultHttpClient();

            HttpRequestBase remoteRequest = createRemoteRequest(request, url);

            long contentLength = HttpHeaders.getContentLength(request, -1);
            if (contentLength > 0 && remoteRequest instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) remoteRequest;
                entityRequest.setEntity(new ByteArrayEntity(request.getContent().array()));
            }

            setupResponse(response, httpclient.execute(remoteRequest));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupResponse(HttpResponse response, org.apache.http.HttpResponse remoteResponse) throws IOException {
        response.setStatus(HttpResponseStatus.valueOf(remoteResponse.getStatusLine().getStatusCode()));

        Header[] allHeaders = remoteResponse.getAllHeaders();
        for (Header header : allHeaders) {
            response.setHeader(header.getName(), header.getValue());
        }

        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        buffer.writeBytes(EntityUtils.toByteArray(remoteResponse.getEntity()));
        response.setContent(buffer);
    }

    private HttpRequestBase createRemoteRequest(HttpRequest request, URL url) {
        HttpRequestBase remoteRequest = createBaseRequest(url, request);

        HttpVersion protocolVersion = request.getProtocolVersion();
        org.apache.http.HttpVersion remoteVersion = new org.apache.http.HttpVersion(protocolVersion.getMajorVersion(), protocolVersion.getMinorVersion());

        remoteRequest.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, remoteVersion);

        for (Map.Entry<String, String> entry : request.getHeaders()) {
            remoteRequest.addHeader(entry.getKey(), entry.getValue());
        }

        remoteRequest.removeHeaders("Content-Length");
        remoteRequest.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS, false);
        return remoteRequest;
    }

    @Override
    public ResponseHandler apply(final MocoConfig config) {
        return this;
    }

    private HttpRequestBase createBaseRequest(URL url, HttpRequest request) {
        if (request.getMethod() == HttpMethod.GET) {
            return new HttpGet(url.toString());
        }

        if (request.getMethod() == HttpMethod.POST) {
            return new HttpPost(url.toString());
        }

        throw new RuntimeException("unknown HTTP method");
    }

    private URL remoteUrl(HttpRequest request) throws MalformedURLException {
        QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
        QueryStringEncoder encoder = new QueryStringEncoder(this.url.getPath());

        for (Map.Entry<String, List<String>> entry : decoder.getParameters().entrySet()) {
            encoder.addParam(entry.getKey(), entry.getValue().get(0));
        }

        return new URL(this.url.getProtocol(), this.url.getHost(), this.url.getPort(), encoder.toString());
    }
}
