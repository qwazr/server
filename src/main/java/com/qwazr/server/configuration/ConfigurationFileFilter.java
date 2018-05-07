/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
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
 **/
package com.qwazr.server.configuration;

import com.qwazr.utils.WildcardMatcher;
import org.apache.commons.io.filefilter.IOFileFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationFileFilter implements IOFileFilter {

    private final List<Matcher> patterns;
    private final boolean noMatchResult;

    ConfigurationFileFilter(final String[] patternArray) {
        patterns = new ArrayList<>();
        if (patternArray == null) {
            noMatchResult = true;
            return;
        }
        int inclusionCount = 0;
        for (final String pattern : patternArray) {
            if (pattern.startsWith("!"))
                patterns.add(new Matcher(pattern.substring(1), false));
            else {
                patterns.add(new Matcher(pattern, true));
                inclusionCount++;
            }
        }
        noMatchResult = inclusionCount == 0;
    }

    @Override
    final public boolean accept(final File pathname) {
        return accept(pathname.getParentFile(), pathname.getName());
    }

    @Override
    final public boolean accept(final File dir, final String name) {
        if (patterns == null || patterns.isEmpty())
            return true;
        for (Matcher matcher : patterns)
            if (matcher.match(name))
                return matcher.result;
        return noMatchResult;
    }

    private static class Matcher extends WildcardMatcher {

        private final boolean result;

        private Matcher(final String pattern, final boolean result) {
            super(pattern);
            this.result = result;
        }
    }
}
