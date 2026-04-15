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

package com.megaease.easeagent;

import com.google.common.base.Strings;
import lombok.SneakyThrows;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.*;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class Main {
    // 指向 Bootstrap Class loader
    private static final ClassLoader BOOTSTRAP_CLASS_LOADER = null;
    private static final String LIB = "lib/";
    private static final String BOOTSTRAP = "boot/";
    private static final String SLf4J2 = "log4j2/";
    private static final String PLUGINS = "plugins/";
    private static final String LOGGING_PROPERTY = "Logging-Property";
    private static final String EASEAGENT_LOG_CONF = "easeagent.log.conf";
    private static final String EASEAGENT_LOG_CONF_ENV_KEY = "EASEAGENT_LOG_CONF";
    private static final String DEFAULT_AGENT_LOG_CONF = "easeagent-log4j2.xml";
    private static ClassLoader loader;
    private static JarCache JAR_CACHE;

    public static void premain(final String args, final Instrumentation inst) throws Exception {
        // 定位 Main 所在的文件位置
        File jar = getArchiveFileContains();
        JAR_CACHE = JarCache.build(jar);

        // custom classloader
        // 读取 lib/ 目录当前的jar路径
        ArrayList<URL> urls = JAR_CACHE.nestJarUrls(LIB);
        // 合并 plugins/ 和 log4j2/ 目录下的 jar 路径
        urls.addAll(JAR_CACHE.nestJarUrls(PLUGINS));
        urls.addAll(JAR_CACHE.nestJarUrls(SLf4J2));
        // 构造指向 plugins 的文件路径
        File p = new File(jar.getParent() + File.separator + "plugins");
        if (p.exists()) {
            // 合并从原始的 agent jar 中读取 plugins/ 目录下的子 jar 路径
            urls.addAll(directoryPluginUrls(p));
        }

        // 使用agent jar中(lib/, plugins/, log4j2/)的路径构造自定义的 class loader
        loader = buildClassLoader(urls.toArray(new URL[0]));

        // install bootstrap jar
        // 读取 boot/ 目录下的子jar文件列表
        final ArrayList<JarFile> bootUrls = JAR_CACHE.nestJarFiles(BOOTSTRAP);
        // 将 boot/ 目录下的所有子 jar 文件添加到 bootstrap class loader 的搜索路径中
        // boo/ 下的目录实际上是 plugin-api，这些 api 是可以被共享到 bootstrap 中的
        bootUrls.forEach(url -> installBootstrapJar(url, inst));

        // 读取 agent jar 中的 Manifest 文件中的属性，用于获取 Logging-Property 值
        final Attributes attributes = JAR_CACHE.getManifest().getMainAttributes();
        final String loggingProperty = attributes.getValue(LOGGING_PROPERTY);
        // 获取 Bootstrap-Class 属性值，实际为：[build]com.megaease.easeagent.StartBootstrap
        final String bootstrap = attributes.getValue("Bootstrap-Class");

        // 将 log4j2/ 目录下的 jar 的 class loader 设置到 com.megaease.easeagent.log4j2.FinalClassloaderSupplier 的 CLASSLOADER 字段中
        initEaseAgentSlf4j2Dir(JAR_CACHE, loader);

        // 切换日志配置，并调用 ease agent class loader 中的 com.megaease.easeagent.StartBootstrap 的 premain 方法，传入参数 args、inst 和 agent jar 的路径
        switchLoggingProperty(loader, loggingProperty, () -> {
            // 该方法会同步执行，并且在执行前，会将线程的 Class loader 临时替换为 ease agent class loader，执行完成后再切换回原来的 class loader
            // 并且会把 host 的日志配置临时切换为 agent 的日志配置，执行完成后再切换回原来的日志配置

            // 初始化 ease agent class loader 中的 org.slf4j.MDC 类
            initAgentSlf4jMDC(loader);
            // 调用 ease agent class loader 中的 com.megaease.easeagent.StartBootstrap，
            // 并调用其 premain 方法，传入参数 args、inst 和 agent jar 的路径
            loader.loadClass(bootstrap)
                .getMethod("premain", String.class, Instrumentation.class, String.class)
                .invoke(null, args, inst, jar.getPath());
            return null;
        });
    }

    // initAgentSlf4jMDC 对 ease agent class loader 中的 org.slf4j.MDC 的类通过调用remove方法的方式进行初始化
    private static void initAgentSlf4jMDC(ClassLoader loader) {
        // init sfl4j MDC for inner agent
        Class<?> mdcClass;
        try {
            // 从 ease agent class loader 定位并查找 org.slf4j.MDC 类
            mdcClass = loader.loadClass("org.slf4j.MDC");
            // just make a reference to mdcClass avoiding JIT remove
            // 通过调用方法的方式，完成对 org.slf4j.MDC 的初始化
            mdcClass.getMethod("remove", String.class)
                .invoke(null, "EaseAgent");
        } catch (Exception ignored) {
            // ignored
        }
    }

    // installBootstrapJar 将指定 jar 文件添加到 bootstrap class loader 的搜索路径中，
    // 其代表该 jar 中的类可以被 bootstrap class loader 加载，从而使得这些类在整个 JVM 中都可见，避免了类加载冲突的问题。
    private static void installBootstrapJar(JarFile file, Instrumentation inst) {
        inst.appendToBootstrapClassLoaderSearch(file);
    }

    // initEaseAgentSlf4j2Dir 初始化 easeagent class loader 中的 com.megaease.easeagent.log4j2.FinalClassloaderSupplier 的 CLASSLOADER 字段
    // 该字段值为 agent jar/log4j2 目录下所有 jar 的 class loader，且没有父级的 class loader
    private static void initEaseAgentSlf4j2Dir(JarCache archive, final ClassLoader bootstrapLoader) throws Exception {
        // 读取 log4j2/ 目录下的子 jar 的文件路径
        final URL[] slf4j2Urls = archive.nestJarUrls(SLf4J2).toArray(new URL[0]);
        // 构造一个仅包含该 log4j2/ 路径的 class loader，且没有父加载器
        final ClassLoader slf4j2Loader = new URLClassLoader(slf4j2Urls, null);
        // 使用 agent class loader 定位并加载 com.megaease.easeagent.log4j2.FinalClassloaderSupplier
        Class<?> classLoaderSupplier = bootstrapLoader.loadClass("com.megaease.easeagent.log4j2.FinalClassloaderSupplier");
        // 读取 CLASSLOADER 字段，并将其值设置为上面的 slf4j2 class loader
        Field field = classLoaderSupplier.getDeclaredField("CLASSLOADER");
        field.set(null, slf4j2Loader);
    }

    /**
     * Switching the system property temporary could fix the problem of conflict of logging configuration
     * when host used the same logging library as agent.
     *
     * 临时修改系统属性，用于解决当 host 和 agent 使用相同的日志库时，日志配置冲突的问题。
     *
     * @param loader ease agent class loader
     * @param hostKey 此处为 Logging-Property
     */
    private static void switchLoggingProperty(ClassLoader loader, String hostKey, Callable<Void> callable)
        throws Exception {
        // 读取当前线程
        final Thread t = Thread.currentThread();
        // 读取当前线程的类加载器
        final ClassLoader ccl = t.getContextClassLoader();

        // 将当前线程的 class loader 切换为 ease agent class loader
        t.setContextClassLoader(loader);

        // get config from system properties
        // 获取 host 的日志配置
        final String host = System.getProperty(hostKey);
        // 从系统属性(easeagent.log.conf) -> 环境变量(EASEAGENT_LOG_CONF) -> 默认值(easeagent-log4j2.xml)的顺序查找日志配置
        final String agent = getLogConfigPath();

        // Redirect config of host to agent
        // 将 agent 临时覆盖 host 的日志配置
        System.setProperty(hostKey, agent);

        try {
            // 看起来 Callable<Void> 等于 Runnable，但是其存在一个重要特别，Callable 可以抛出异常，而 Runnable 则不会
            callable.call();
        } finally {
            // 将 Thread 的 class loader 切换回原来的 class loader
            t.setContextClassLoader(ccl);
            // Recovery host configuration
            if (host == null) {
                // 如果原先没有配置，则移除
                System.getProperties().remove(hostKey);
            } else {
                // 原先配置了，则切换回原先的配置
                System.setProperty(hostKey, host);
            }
        }
    }

    // getLogConfigPath 查找日志配置，从系统属性(easeagent.log.conf) -> 环境变量(EASEAGENT_LOG_CONF) -> 默认值(easeagent-log4j2.xml)的顺序查找
    private static String getLogConfigPath() {
        // 从系统属性中读取 easeagent.log.conf
        String logConfigPath = System.getProperty(EASEAGENT_LOG_CONF);
        if (Strings.isNullOrEmpty(logConfigPath)) {
            // 从环境变量中读取 EASEAGENT_LOG_CONF
            logConfigPath = System.getenv(EASEAGENT_LOG_CONF_ENV_KEY);

        }
        // if not set, use default
        // 如果没有设值，则使用默认的 easeagent-log4j2.xml
        if (Strings.isNullOrEmpty(logConfigPath)) {
            logConfigPath = DEFAULT_AGENT_LOG_CONF;
        }
        return logConfigPath;
    }

    // directoryPluginUrls 从指定目录下读取 jar 文件的 url 列表
    private static ArrayList<URL> directoryPluginUrls(File directory) {
        if (!directory.isDirectory()) {
            return new ArrayList<>();
        }

        // 获取该目录下的所有文件
        File[] files = directory.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }

        final ArrayList<URL> urls = new ArrayList<>(files.length);

        Arrays.stream(files).forEach(item -> {
            // 只过滤以 jar 结尾的文件
            if (!item.getName().endsWith("jar")) {
                return;
            }
            try {
                URL pUrl = item.toURI().toURL();
                urls.add(pUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });
        return urls;
    }

    // getArchiveFileContains 获取代理 jar 的文件路径
    // 主要根据 Main.class 所在的问题去定位 jar 包的位置
    private static File getArchiveFileContains() throws URISyntaxException {
        final ProtectionDomain protectionDomain = Main.class.getProtectionDomain();
        final CodeSource codeSource = protectionDomain.getCodeSource();
        final URI location = (codeSource == null ? null : codeSource.getLocation().toURI());
        final String path = (location == null ? null : location.getSchemeSpecificPart());

        if (path == null) {
            throw new IllegalStateException("Unable to determine code source archive");
        }

        final File root = new File(path);
        if (!root.exists() || root.isDirectory()) {
            throw new IllegalStateException("Unable to determine code source archive from " + root);
        }
        return root;
    }

    // buildClassLoader 使用指定的 url 构造自定义的 class loader，此处父加载器为 null，表示不委托给父加载器加载类
    static ClassLoader buildClassLoader(URL[] urls) {
        return new EaseAgentClassLoader(urls, BOOTSTRAP_CLASS_LOADER);
    }

    @SneakyThrows
    public static void main(String[] args) {
        // ignored
    }
}
