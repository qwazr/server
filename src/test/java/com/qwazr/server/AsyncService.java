/*
 * Copyright 2015-2018 Emmanuel Keller / QWAZR
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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

@Path("/")
public class AsyncService {

	private final ExecutorService executorService;

	public AsyncService(ExecutorService executorService) {
		this.executorService = executorService;
	}

	@Path("/async")
	@GET
	public CompletionStage<String> async(@QueryParam("test") String test) {
		final CompletableFuture<String> completion = new CompletableFuture<>();
		executorService.submit(() -> {
			Thread.sleep(1000);
			completion.complete(test);
			return test;
		});
		return completion;
	}

}
