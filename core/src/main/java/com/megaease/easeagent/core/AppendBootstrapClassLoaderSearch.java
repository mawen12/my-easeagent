/*
 * Copyright (c) 2017, MegaEase
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
 */

package com.megaease.easeagent.core;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.megaease.easeagent.plugin.AppendBootstrapLoader;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.pool.TypePool;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.FluentIterable.from;
import static com.google.common.collect.Maps.uniqueIndex;
import static java.util.Collections.list;

public final class AppendBootstrapClassLoaderSearch {
    // 这是指向临时目录的文件对象
    private static final File TMP_FILE = new File(
        AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getProperty("java.io.tmpdir");
                }
            })
    );

    // by 使用 ByteBuddy 通过 ease agent class loader 查找 META-INF/services/com.megaease.easeagent.plugin.AppendBootstrapLoader
    // 然后将其中的类注入到 bootstrap class loader 中，完成类的注入
    static Set<String> by(Instrumentation inst, ClassInjector.UsingInstrumentation.Target target) throws IOException {
        // 读取 META-INF/services/com.megaease.easeagent.plugin.AppendBootstrapLoader 中的内容
        final Set<String> names = findClassAnnotatedAutoService(AppendBootstrapLoader.class);
        // 使用 ByteBuddy 将类注入到 bootstrap class loader 中，此处的 Target 指向的是 Bootstrap Class loader
        // ByteBuddy 底层会为这些类名在 /tmp 目录下生成临时的 jar 文件，其中只包含这些 names
        // 然后再将该 JarFile 注入到 bootstrap class loader 中，完成类的注入
        ClassInjector.UsingInstrumentation.of(TMP_FILE, target, inst).inject(types(names));
        return names;
    }

    // types 使用 TypePool 获取类名的类名，使用 ClassFileLocator 读取类的字节码，并将类名和字节码组成 Map 返回
    private static Map<TypeDescription, byte[]> types(Set<String> names) {
        final ClassLoader loader = AppendBootstrapClassLoaderSearch.class.getClassLoader();
        // 使用 ByteBuddy 中的 ClassFileLocator + ease agent class loader来定位和解析类
        final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(loader);
        final TypePool pool = TypePool.Default.of(locator);

        return Maps.transformValues(
            // 根据类名查找类型定义，并将其作为 Key，Value 仍然是 name
            uniqueIndex(names, input -> pool.describe(input).resolve()),
            // 对 name 进行处理，使用 locator 定位类，并解析成字节码数组
            input -> {
                try {
                    return locator.locate(input).resolve();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
    }

    // findClassAnnotatedAutoService 从 ease agent class loader 中读取 META-INF/services/com.megaease.easeagent.plugin.AppendBootstrapLoader 的内容
    private static Set<String> findClassAnnotatedAutoService(Class<?> cls) throws IOException {
        // 读取类加载器，当前加载器为 ease agent class loader
        final ClassLoader loader = AppendBootstrapClassLoaderSearch.class.getClassLoader();

        // 读取位于 META-INF/services/com.megaease.easeagent.plugin.AppendBootstrapLoader 的资源内容
        // 该类实际位于 lib/ 目录下
        return from(list(loader.getResources("META-INF/services/" + cls.getName())))
            .transform(input -> {
                try {
                    final URLConnection connection = input.openConnection();
                    final InputStream stream = connection.getInputStream();
                    return new InputStreamReader(stream, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            })
            .transformAndConcat((Function<InputStreamReader, Iterable<String>>) input -> {
                try {
                    // 按行读取
                    return CharStreams.readLines(input);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                } finally {
                    Closeables.closeQuietly(input);
                }

            })
            .toSet();
    }

    private AppendBootstrapClassLoaderSearch() {
    }
}
