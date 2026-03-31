/*
 * Copyright (c) 2022, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.megaease.easeagent.core.plugin.matcher;

import com.megaease.easeagent.core.Bootstrap;
import com.megaease.easeagent.log4j2.FinalClassloaderSupplier;
import com.megaease.easeagent.plugin.matcher.loader.IClassLoaderMatcher;
import com.megaease.easeagent.plugin.matcher.loader.NegateClassLoaderMatcher;
import net.bytebuddy.matcher.ElementMatcher;

import static com.megaease.easeagent.plugin.matcher.loader.ClassLoaderMatcher.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * ClassLoaderMatcher 转换器，从 ease agent 的 IClassLoaderMatcher 转换为 ByteBuddy 的 ElementMatcher<ClassLoader>
 */
public class ClassLoaderMatcherConvert implements Converter<IClassLoaderMatcher, ElementMatcher<ClassLoader>> {
    public static final ClassLoaderMatcherConvert INSTANCE = new ClassLoaderMatcherConvert();

    // 该 agent loader 实际指向的是 EaseAgentClassLoader
    private static final ElementMatcher<ClassLoader> agentLoaderMatcher = is(Bootstrap.class.getClassLoader())
        .or(is(FinalClassloaderSupplier.CLASSLOADER));

    // convert 从 ease agent 的 IClassLoaderMatcher 转换为 byte buddy 的 ElementMatcher<ClassLoader>
    @Override
    public ElementMatcher<ClassLoader> convert(IClassLoaderMatcher source) {
        boolean negate;
        ElementMatcher<ClassLoader> matcher;

        // 处理 Negate
        if (source instanceof NegateClassLoaderMatcher) {
            negate = true;
            source = source.negate();
        } else {
            negate = false;
        }

        // 处理 ALL
        if (ALL.equals(source)) {
            // ALL -> ElementMatchers#any
            matcher = any();
        } else {
            switch (source.getClassLoaderName()) {
                // BOOTSTRAP_NAME -> ElementMatchers#isBootstrapClassLoader
                case BOOTSTRAP_NAME:
                    matcher = isBootstrapClassLoader();
                    break;
                // EXTERNAL_NAME -> ElementMatchers#isExtensionClassLoader
                case EXTERNAL_NAME:
                    matcher = isExtensionClassLoader();
                    break;
                // SYSTEM_NAME -> ElementMatchers#isSystemClassLoader
                case SYSTEM_NAME:
                    matcher = isSystemClassLoader();
                    break;
                // AGENT_NAME -> EaseAgentClassLoader
                case AGENT_NAME:
                    matcher = agentLoaderMatcher;
                    break;
                // -> NameMatcher
                default:
                    matcher = new NameMatcher(source.getClassLoaderName());
                    break;
            }
        }

        if (negate) {
            return not(matcher);
        } else {
            return matcher;
        }
    }

    static class NameMatcher implements ElementMatcher<ClassLoader> {
        final String className;

        public NameMatcher(String name) {
            this.className = name;
        }

        @Override
        public boolean matches(ClassLoader target) {
            if (target == null) {
                return this.className == null;
            }
            return this.className.equals(target.getClass().getCanonicalName());
        }
    }
}
