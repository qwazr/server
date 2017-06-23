/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.qwazr.server.AbstractStreamingOutput;
import com.qwazr.server.response.ResponseValidator;
import com.qwazr.utils.http.HttpRequest;
import org.apache.http.HttpResponse;

public interface JsonClientInterface {

	/**
	 * Execute an HTTP request returning the expected object.
	 *
	 * @param request         the HTTP request to execute
	 * @param bodyObject      an optional object for the body.
	 * @param msTimeOut       an optional timeout in milliseconds
	 * @param jsonResultClass The class of the returned object
	 * @param validator       The response validator
	 * @param <T>             The type of the returned object
	 * @return An instance of the expected class
	 */
	<T> T executeJson(HttpRequest request, Object bodyObject, Integer msTimeOut, Class<T> jsonResultClass,
			ResponseValidator validator);

	/**
	 * Execute an HTTP request returning an objet of the expected type
	 * reference.
	 *
	 * @param request    the HTTP request to execute
	 * @param msTimeOut  an optional timeout in milliseconds
	 * @param bodyObject an optional object for the body
	 * @param typeRef    the TypeRef of the returned object
	 * @param validator  The response validator
	 * @param <T>        The type of the returned object
	 * @return An instance of the expected type
	 */
	<T> T executeJson(HttpRequest request, Object bodyObject, Integer msTimeOut, TypeReference<T> typeRef,
			ResponseValidator validator);

	/**
	 * @param request    the HTTP request to execute
	 * @param bodyObject an optional object for the body
	 * @param msTimeOut  an optional timeout in milliseconds
	 * @param validator  The response validator
	 * @return a new JsonNode object
	 */
	JsonNode executeJsonNode(HttpRequest request, Object bodyObject, Integer msTimeOut, ResponseValidator validator);

	/**
	 * @param request    the HTTP request to execute
	 * @param bodyObject an optional object for the body
	 * @param msTimeOut  an optional timeout in milliseconds
	 * @param validator  The response validator
	 * @return the HTTP response
	 */
	HttpResponse execute(HttpRequest request, Object bodyObject, Integer msTimeOut, ResponseValidator validator);

	/**
	 * @param request    the HTTP request to execute
	 * @param bodyObject an optional object for the body
	 * @param msTimeOut  an optional timeout in milliseconds
	 * @param validator  The response validator
	 * @return the HTTP status code
	 */
	Integer executeStatusCode(HttpRequest request, Object bodyObject, Integer msTimeOut, ResponseValidator validator);

	/**
	 * Execute an HTTP request. The bodyObject is sent as payload if it is not
	 * null. If it is a String object, it is send as PLAIN/TEXT. If it is
	 * another object, it is serialized in JSON format.
	 *
	 * @param request    A preconfigured HTTP request
	 * @param bodyObject The body of the request (payload)
	 * @param msTimeOut  The time out in milliseconds. If null, the default value is
	 *                   used
	 * @param validator  The response validator
	 * @return the content of the HTTP entity in String Format
	 */
	String executeString(HttpRequest request, Object bodyObject, Integer msTimeOut, ResponseValidator validator);

	/**
	 * Execute an HTTP request. The bodyObject is sent as payload if it is not
	 * null. If it is a String object, it is send as PLAIN/TEXT. If it is
	 * another object, it is serialized in JSON format.
	 *
	 * @param request    A preconfigured HTTP request
	 * @param bodyObject The body of the request (payload)
	 * @param msTimeOut  The time out in milliseconds. If null, the default value is
	 *                   used
	 * @param validator  The response validator
	 * @return the content of the HTTP entity in stream format
	 */
	AbstractStreamingOutput executeStream(HttpRequest request, Object bodyObject, Integer msTimeOut,
			ResponseValidator validator);

}
