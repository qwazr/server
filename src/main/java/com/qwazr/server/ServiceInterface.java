/**
 * s * Copyright 2016 Emmanuel Keller / QWAZR
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
package com.qwazr.server;

import com.qwazr.server.response.ResponseValidator;
import org.apache.http.entity.ContentType;

public interface ServiceInterface {

	String APPLICATION_JSON_UTF8 = "application/json; charset=UTF-8";

	ResponseValidator valid200 = ResponseValidator.create().status(200);
	ResponseValidator valid200202 = ResponseValidator.create().status(200, 202);
	ResponseValidator valid200204 = ResponseValidator.create().status(200, 204);
	ResponseValidator valid200202Json =
			ResponseValidator.create().status(200, 202).content(ContentType.APPLICATION_JSON);
	ResponseValidator valid200TextPlain = ResponseValidator.create().status(200).content(ContentType.TEXT_PLAIN);
	ResponseValidator valid200Json = ResponseValidator.create().status(200).content(ContentType.APPLICATION_JSON);
	ResponseValidator valid200Stream =
			ResponseValidator.create().status(200).content(ContentType.APPLICATION_OCTET_STREAM);
}
