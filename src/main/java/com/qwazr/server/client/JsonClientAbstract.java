/**
 * Copyright 2014-2016 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.server.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.qwazr.server.AbstractStreamingOutput;
import com.qwazr.server.RemoteService;
import com.qwazr.server.ServerException;
import com.qwazr.server.response.HttpResponseHandler;
import com.qwazr.server.response.JsonHttpResponseHandler;
import com.qwazr.server.response.ResponseValidator;
import com.qwazr.server.response.StringHttpResponseHandler;
import com.qwazr.utils.IOUtils;
import com.qwazr.utils.http.HttpClients;
import com.qwazr.utils.http.HttpRequest;
import com.qwazr.utils.json.JsonMapper;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;

import java.io.IOException;
import java.io.InputStream;

public abstract class JsonClientAbstract implements JsonClientInterface {

	public final static String CONTENT_TYPE_JSON_UTF8 = ContentType.APPLICATION_JSON.toString();
	private final static int DEFAULT_TIMEOUT;

	static {
		String s = System.getProperty("com.qwazr.server.client.default_timeout");
		DEFAULT_TIMEOUT = s == null ? 60000 : Integer.parseInt(s);
	}

	protected final RemoteService remote;

	final private AuthCache authCache;
	final private BasicCredentialsProvider credentialsProvider;
	final private BasicCookieStore cookieStore;

	protected JsonClientAbstract(final RemoteService remote) {
		this.remote = remote;

		final Credentials credentials = remote.getCredentials();

		authCache = new BasicAuthCache();
		credentialsProvider = new BasicCredentialsProvider();
		if (credentials != null)
			credentialsProvider.setCredentials(AuthScope.ANY, credentials);
		cookieStore = new BasicCookieStore();
	}

	private HttpClientContext getContext(final Integer msTimeOut) {
		final HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(credentialsProvider);
		context.setAuthCache(authCache);
		context.setCookieStore(cookieStore);
		final RequestConfig.Builder requestConfig = RequestConfig.custom();
		final int timeout = msTimeOut != null ? msTimeOut : remote.timeout != null ? remote.timeout : DEFAULT_TIMEOUT;
		requestConfig.setSocketTimeout(timeout).setConnectTimeout(timeout).setConnectionRequestTimeout(timeout);
		context.setAttribute(HttpClientContext.REQUEST_CONFIG, requestConfig.build());
		return context;
	}

	private void setBody(final HttpRequest request, final Object bodyObject) throws JsonProcessingException {
		if (bodyObject == null)
			return;
		final HttpRequest.Entity requestEntity = (HttpRequest.Entity) request;
		if (bodyObject instanceof String)
			requestEntity.bodyString(bodyObject.toString(), ContentType.TEXT_PLAIN);
		else if (bodyObject instanceof InputStream)
			requestEntity.bodyStream((InputStream) bodyObject, ContentType.APPLICATION_OCTET_STREAM);
		else
			requestEntity.bodyString(JsonMapper.MAPPER.writeValueAsString(bodyObject), ContentType.APPLICATION_JSON);
	}

	private <T> T executeJsonEx(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final Class<T> jsonResultClass, final ResponseValidator validator) throws IOException {
		setBody(request, bodyObject);
		final JsonHttpResponseHandler.JsonValueResponse<T> responseHandler =
				new JsonHttpResponseHandler.JsonValueResponse<T>(jsonResultClass, validator);
		request.addHeader("Accept", CONTENT_TYPE_JSON_UTF8);
		return HttpClients.HTTP_CLIENT.execute(request.request, responseHandler, getContext(msTimeOut));
	}

	@Override
	final public <T> T executeJson(final HttpRequest request, final Object body, final Integer msTimeOut,
			final Class<T> objectClass, final ResponseValidator validator) {
		try {
			return executeJsonEx(request, body, msTimeOut, objectClass, validator);
		} catch (IOException e) {
			throw ServerException.getServerException(e).getJsonException();
		}
	}

	private <T> T executeJsonEx(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final TypeReference<T> typeRef, final ResponseValidator validator) throws IOException {
		setBody(request, bodyObject);
		return HttpClients.HTTP_CLIENT.execute(request.addHeader("accept", CONTENT_TYPE_JSON_UTF8).request,
				new JsonHttpResponseHandler.JsonValueTypeRefResponse<>(typeRef, validator), getContext(msTimeOut));
	}

	@Override
	final public <T> T executeJson(final HttpRequest request, final Object body, final Integer msTimeOut,
			final TypeReference<T> typeRef, final ResponseValidator validator) {
		try {
			return executeJsonEx(request, body, msTimeOut, typeRef, validator);
		} catch (IOException e) {
			throw ServerException.getServerException(e).getJsonException();
		}
	}

	private JsonNode executeJsonNodeEx(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final ResponseValidator validator) throws IOException {
		setBody(request, bodyObject);
		return HttpClients.HTTP_CLIENT.execute(request.addHeader("accept", CONTENT_TYPE_JSON_UTF8).request,
				new JsonHttpResponseHandler.JsonTreeResponse(validator), getContext(msTimeOut));
	}

	@Override
	final public JsonNode executeJsonNode(final HttpRequest request, final Object body, final Integer msTimeOut,
			final ResponseValidator validator) {
		try {
			return executeJsonNodeEx(request, body, msTimeOut, validator);
		} catch (IOException e) {
			throw ServerException.getServerException(e).getJsonException();
		}
	}

	private HttpResponse executeEx(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final ResponseValidator validator) throws IOException {
		setBody(request, bodyObject);
		return HttpClients.HTTP_CLIENT.execute(request.request, new HttpResponseHandler(validator),
				getContext(msTimeOut));
	}

	@Override
	final public HttpResponse execute(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final ResponseValidator validator) {
		try {
			return executeEx(request, bodyObject, msTimeOut, validator);
		} catch (IOException e) {
			throw ServerException.getServerException(e).getTextException();
		}
	}

	@Override
	final public Integer executeStatusCode(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final ResponseValidator validator) {
		try {
			HttpResponse response = executeEx(request, bodyObject, msTimeOut, validator);
			return response.getStatusLine().getStatusCode();
		} catch (IOException e) {
			throw ServerException.getServerException(e).getTextException();
		}
	}

	private String executeStringEx(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final ResponseValidator validator) throws IOException {
		setBody(request, bodyObject);
		return HttpClients.HTTP_CLIENT.execute(request.request, new StringHttpResponseHandler(validator),
				getContext(msTimeOut));
	}

	@Override
	final public String executeString(final HttpRequest request, final Object bodyObject, final Integer msTimeOut,
			final ResponseValidator validator) {
		try {
			return executeStringEx(request, bodyObject, msTimeOut, validator);
		} catch (IOException e) {
			throw ServerException.getServerException(e).getTextException();
		}
	}

	private AbstractStreamingOutput executeStreamEx(final HttpRequest request, final Object bodyObject,
			final Integer msTimeOut, final ResponseValidator validator) throws IOException {
		setBody(request, bodyObject);
		final CloseableHttpResponse response = HttpClients.HTTP_CLIENT.execute(request.request, getContext(msTimeOut));
		if (validator != null) {
			try {
				validator.checkResponse(response.getAllHeaders(), response.getStatusLine(), response.getEntity());
			} catch (ClientProtocolException e) {
				IOUtils.closeQuietly(response);
				throw e;
			}
		}
		return AbstractStreamingOutput.with(response);
	}

	@Override
	final public AbstractStreamingOutput executeStream(final HttpRequest request, final Object bodyObject,
			final Integer msTimeOut, final ResponseValidator validator) {
		try {
			return executeStreamEx(request, bodyObject, msTimeOut, validator);
		} catch (IOException e) {
			throw ServerException.getServerException(e).getTextException();
		}
	}

}
