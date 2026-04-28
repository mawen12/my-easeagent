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

package com.megaease.easeagent.config;

import com.megaease.easeagent.config.yaml.YamlReader;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import org.yaml.snakeyaml.parser.ParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 用于读取 properties/yml，作为全局配置
 */
public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    private static boolean checkYaml(String filename) {
        return filename.endsWith(".yaml") || filename.endsWith(".yml");
    }

    // loadFromFile 从文件中读取配置，支持 yaml/properties 两种格式
    static GlobalConfigs loadFromFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            return ConfigLoader.loadFromStream(in, file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.warn("Load config file failure: {}", file.getAbsolutePath());
        }
        return new GlobalConfigs(Collections.emptyMap());
    }

    // loadFromStream 从输入流读取配置，支持 yaml/properties 两种格式
    static GlobalConfigs loadFromStream(InputStream in, String filename) throws IOException {
        if (in != null) {
            Map<String, String> map;
            // 根据文件名称后缀判断是 yaml 还是 properties 文件，yaml 文件使用 YamlReader 解析，properties 文件使用 Properties 解析
            if (checkYaml(filename)) {
                try {
                    // 读取 yaml 配置
                    map = new YamlReader().load(in).compress();
                } catch (ParserException e) {
                    LOGGER.warn("Wrong Yaml format, load config file failure: {}", filename);
                    map = Collections.emptyMap();
                }
            } else {
                // 读取 properties 配置
                map = extractPropsMap(in);
            }
            return new GlobalConfigs(map);
        } else {
            return new GlobalConfigs(Collections.emptyMap());
        }
    }

    // extractPropsMap 将配置读取到properties，再转换为 hashmap
    private static HashMap<String, String> extractPropsMap(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        HashMap<String, String> map = new HashMap<>();
        for (String one : properties.stringPropertyNames()) {
            map.put(one, properties.getProperty(one));
        }
        return map;
    }

    static GlobalConfigs loadFromClasspath(ClassLoader classLoader, String file) {
        try (InputStream in = classLoader.getResourceAsStream(file)) {
            return ConfigLoader.loadFromStream(in, file);
        } catch (IOException e) {
            LOGGER.warn("Load config file:{} by classloader:{} failure: {}", file, classLoader.toString(), e);
        }

        return new GlobalConfigs(Collections.emptyMap());
    }
}
