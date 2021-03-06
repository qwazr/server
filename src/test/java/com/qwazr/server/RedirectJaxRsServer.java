/*
 * Copyright 2016-2018 Emmanuel Keller / QWAZR
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

import com.qwazr.server.configuration.ServerConfiguration;

import java.io.IOException;

public class RedirectJaxRsServer implements BaseServer {

    private GenericServer server;

    RedirectJaxRsServer() throws IOException {
        final GenericServerBuilder builder = GenericServer.of(ServerConfiguration.of().build());
        builder.getWebServiceContext()
                .jaxrs(ApplicationBuilder.of("/*")
                        .loadServices()
                        .singletons(new RedirectService("http://localhost:9091")));
        server = builder.build();
    }

    @Override
    public GenericServer getServer() {
        return server;
    }

}
