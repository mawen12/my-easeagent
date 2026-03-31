/*
 * Copyright (c) 2021, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * EaseAgent's jar reading cache.
 * Only one layer of sub-jar packages is read, and multi-layer nested reading is not supported.
 * easeagent has only one layer of sub-jar packages, so only one layer needs to be read.
 * It does not need to use spring-boot's loader, which reduces dependencies and facilitates spring-boot business upgrades
 */
public class JarCache {
    private static final int EOF = -1;
    private static final int BUFFER_SIZE = 4096;

    private final JarFile jarFile;
    private final Map<String, JarFile> childJars;
    private final Map<String, URL> childUrls;

    public JarCache(JarFile jarFile, Map<String, JarFile> childJars, Map<String, URL> childUrls) throws IOException {
        this.jarFile = jarFile;
        this.childJars = childJars;
        this.childUrls = childUrls;
    }

    // nestJarUrls 读取子 jar 中匹配前缀的 url 列表
    public ArrayList<URL> nestJarUrls(String prefix) {
        ArrayList<URL> urls = new ArrayList<>();
        for (Map.Entry<String, URL> entry : childUrls.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                urls.add(entry.getValue());
            }
        }
        return urls;
    }

    // nestJarFiles 读取子 jar 中匹配前缀的 jar 文件列表
    public ArrayList<JarFile> nestJarFiles(String prefix) {
        ArrayList<JarFile> jarFiles = new ArrayList<>();
        for (Map.Entry<String, JarFile> entry : childJars.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                jarFiles.add(entry.getValue());
            }
        }
        return jarFiles;
    }

    public Manifest getManifest() throws IOException {
        return this.jarFile.getManifest();
    }


    // build 解析给定的文件路径，从中读取 jar 文件，并将子 jar 文件的内容写入到临时文件中
    static JarCache build(File file) throws IOException {
        final JarFile jarFile = new JarFile(file);
        // 生成一个随机目录，/tmp/easeagent-<version>-随机数/
        String tmpDir = getTmpDir(jarFile);
        Map<String, JarFile> childJars = new HashMap<>();
        Map<String, URL> childUrls = new HashMap<>();
        jarFile.stream().forEach(jarEntry -> {
            String name = jarEntry.getName();
            // 不考虑目录，仅处理 .jar 中的内容
            if (!jarEntry.isDirectory() && name.endsWith(".jar")) {
                // 将 jarEntry 中的内容写入到临时文件中，并返回文件
                try (InputStream input = jarFile.getInputStream(jarEntry)) {
                    File output = createTempJarFile(tmpDir, input, jarEntry.getName());
                    JarFile childJarFile = new JarFile(output);
                    // 保存子 jar 文件和对应的 URL
                    childJars.put(name, childJarFile);
                    childUrls.put(name, output.toURI().toURL());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return new JarCache(jarFile, childJars, childUrls);
    }

    // createTempJarFile 将输入流中的数据写入到临时文件中，并返回文件（考虑了路径名称过长的问题）
    private static File createTempJarFile(String tmpDir, InputStream input, String outputName) throws IOException {
        File dir;
        String fName = (new File(outputName)).getName();
        // 处理路径名称过长的问题
        if (fName.length() < outputName.length()) {
            // 获取被阶段的目录的名称
            String localDir = outputName.substring(0, outputName.length() - fName.length());
            // 在 tmpDir/localDir/ 作为父级目录
            Path path = Paths.get(tmpDir + File.separatorChar + localDir);
            dir = Files.createDirectories(path).toFile();
        } else {
            // 直接使用 tmpDir 作为父级目录
            dir = new File(tmpDir);
        }
        // 写入到 dir/fName 中
        File f = new File(dir, fName);
        // 首先删除已存在的文件名称
        f.deleteOnExit();
        // 写入本地文件
        try (FileOutputStream outputStream = new FileOutputStream(f)) {
            copy(input, outputStream);
        }

        return f;
    }

    // copy 从输入流读取数据，并将其复制到输出流
    public static void copy(InputStream input, OutputStream output) throws IOException {
        int n;
        final byte[] buffer = new byte[BUFFER_SIZE];
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
    }

    // getTmpDir 在临时目录下生成一个随机目录，/tmp/easeagent-<version>-随机数/
    public static String getTmpDir(JarFile jarFile) throws IOException {
        // 读取临时目录
        String tmp = System.getProperty("java.io.tmpdir");
        Random random = new Random();
        // 生成一个随机目录名称，easeagent-<version>-随机数
        String dirName = "easeagent-" + getAttribute(jarFile, "Easeagent-Version") + "-" + Math.abs(random.nextLong());
        if (tmp != null && tmp.endsWith(String.valueOf(File.separatorChar))) {
            return tmp + dirName + File.separatorChar;
        }
        return tmp + File.separatorChar + dirName + File.separatorChar;
    }

    // getAttribute 从 mainifest 中读取指定属性值
    public static String getAttribute(JarFile jarFile, String key) throws IOException {
        final Attributes attributes = jarFile.getManifest().getMainAttributes();
        return attributes.getValue(key);
    }

}
