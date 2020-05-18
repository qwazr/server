/*
 * Copyright 2017-2020 Emmanuel Keller / QWAZR
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

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.util.ImmediateInstanceHandle;

import java.util.function.Supplier;

public interface GenericFactory<T> extends InstanceFactory<T> {

    static <T> GenericFactory<T> fromInstance(final T instance) {
        return new FromInstance<>(instance);
    }

    static <T> GenericFactory<T> fromSupplier(final Supplier<T> supplier) {
        return new FromSupplier<>(supplier);
    }

    final class FromInstance<T> implements GenericFactory<T> {

        protected final T instance;

        private FromInstance(final T instance) {
            this.instance = instance;
        }

        @Override
        public InstanceHandle<T> createInstance() {
            return new ImmediateInstanceHandle<>(instance);
        }
    }

    final class FromSupplier<T> implements GenericFactory<T> {

        private final Supplier<T> supplier;

        private FromSupplier(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public InstanceHandle<T> createInstance() {
            return new ImmediateInstanceHandle<>(supplier.get());
        }
    }
}
