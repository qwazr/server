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

package com.qwazr.server.response;

import com.qwazr.server.StreamFileUpload;
import com.qwazr.utils.IOUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DownloadHttpResponseHandler extends AbstractHttpResponseHandler<List<FileItem>> {

	private final File repository;

	public DownloadHttpResponseHandler(final File repository, final ResponseValidator validator) {
		super(validator);
		this.repository = repository;
	}

	public List<FileItem> handleResponse(final HttpResponse response) throws IOException {
		super.handleResponse(response);
		try {
			return new StreamFileUpload(repository).parse(entity);
		} catch (FileUploadException e) {
			throw new IOException(e);
		} finally {
			IOUtils.close((CloseableHttpResponse) response);
		}
	}
}
