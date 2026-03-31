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

package com.megaease.easeagent.config;

import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.utils.ImmutableMap;
import com.megaease.easeagent.plugin.utils.SystemEnv;
import com.megaease.easeagent.plugin.utils.common.JsonUtil;
import com.megaease.easeagent.plugin.utils.common.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ConfigFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigFactory.class);
    private static final String CONFIG_PROP_FILE = "agent.properties";
    private static final String CONFIG_YAML_FILE = "agent.yaml";

    public static final String AGENT_CONFIG_PATH_PROP_KEY = "easeagent.config.path";

    public static final String AGENT_SERVICE = "name";
    public static final String AGENT_SYSTEM = "system";

    public static final String AGENT_SERVER_PORT = "easeagent.server.port";
    public static final String AGENT_SERVER_ENABLED = "easeagent.server.enabled";

    public static final String EASEAGENT_ENV_CONFIG = "EASEAGENT_ENV_CONFIG";

    private static final Map<String, String> AGENT_CONFIG_KEYS_TO_PROPS =
        ImmutableMap.<String, String>builder()
            .put("easeagent.name", AGENT_SERVICE)
            .put("easeagent.system", AGENT_SYSTEM)
            .put("easeagent.server.port", AGENT_SERVER_PORT)
            .put("easeagent.server.enabled", AGENT_SERVER_ENABLED)
            .build();

    // OTEL_SERVICE_NAME=xxx
    private static final Map<String, String> AGENT_ENV_KEY_TO_PROPS = new HashMap<>();


    static {
        for (Map.Entry<String, String> entry : AGENT_CONFIG_KEYS_TO_PROPS.entrySet()) {
            // dot.case -> UPPER_UNDERSCORE
            AGENT_ENV_KEY_TO_PROPS.put(
                ConfigPropertiesUtils.toEnvVarName(entry.getKey()),
                entry.getValue()
            );
        }
    }

    /**
     * update config value from environment variables and java properties
     * <p>
     * java properties > environment variables > env:EASEAGENT_ENV_CONFIG={} > default
     */
    static Map<String, String> updateEnvCfg() {
        Map<String, String> envCfg = new TreeMap<>();

        String configEnv = SystemEnv.get(EASEAGENT_ENV_CONFIG);
        if (StringUtils.isNotEmpty(configEnv)) {
            Map<String, Object> map = JsonUtil.toMap(configEnv);
            Map<String, String> strMap = new HashMap<>();
            if (!map.isEmpty()) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    strMap.put(entry.getKey(), entry.getValue().toString());
                }
            }
            envCfg.putAll(strMap);
        }

        // override by environment variables, eg: export EASEAGENT_NAME=xxx
        for (Map.Entry<String, String> entry : AGENT_ENV_KEY_TO_PROPS.entrySet()) {
            String value = SystemEnv.get(entry.getKey());
            if (!StringUtils.isEmpty(value)) {
                envCfg.put(entry.getValue(), value);
            }
        }

        // override by java properties; eg: java -Deaseagent.name=xxx
        for (Map.Entry<String, String> entry : AGENT_CONFIG_KEYS_TO_PROPS.entrySet()) {
            String value = System.getProperty(entry.getKey());
            if (!StringUtils.isEmpty(value)) {
                envCfg.put(entry.getValue(), value);
            }
        }

        return envCfg;
    }

    private ConfigFactory() {
    }

    /**
     * Get config file path from system properties or environment variables
     *
     * 从系统属性或环境变量中获取配置文件路径，key从 easeagent.config.path -> otel.javaagent.configuration-file
     */
    public static String getConfigPath() {
        // get config path from -Deaseagent.config.path=/easeagent/agent.properties || export EASEAGENT_CONFIG_PATH=/easeagent/agent.properties
        // 从系统属性或环境变量中获取配置文件路径
        String path = ConfigPropertiesUtils.getString(AGENT_CONFIG_PATH_PROP_KEY);

        // 如果为空，则从 otel.javaagent.configuration-file 属性或变量获取值
        if (StringUtils.isEmpty(path)) {
            // eg: -Dotel.javaagent.configuration-file=/easeagent/agent.properties || export OTEL_JAVAAGENT_CONFIGURATION_FILE=/easeagent/agent.properties
            path = OtelSdkConfigs.getConfigPath();
        }
        return path;
    }

    // loadConfigs 读取 agent.properties 和 agent.yaml 文件的配置
    // 再读取自定义的路径配置，然后进行合并
    public static GlobalConfigs loadConfigs(String pathname, ClassLoader loader) {
        // load property configuration file if exist
        // 读取 agent.properties 文件的配置
        GlobalConfigs configs = loadDefaultConfigs(loader, CONFIG_PROP_FILE);

        // load yaml configuration file if exist
        // 读取 agent.yaml 文件的配置
        GlobalConfigs yConfigs = loadDefaultConfigs(loader, CONFIG_YAML_FILE);
        // 合并配置
        configs.mergeConfigs(yConfigs);

        // override by user special config file
        // 读取用户自定义配置的路径，如果不为空，则加载该路径的配置，并进行合并
        if (StringUtils.isNotEmpty(pathname)) {
            GlobalConfigs configsFromOuterFile = ConfigLoader.loadFromFile(new File(pathname));
            LOGGER.info("Loaded user special config file: {}", pathname);
            configs.mergeConfigs(configsFromOuterFile);
        }

        // override by opentelemetry sdk env config
        // 读取 opentelemetry sdk 的环境变量配置，并进行合并
        configs.updateConfigsNotNotify(OtelSdkConfigs.updateEnvCfg());

        // check environment cfg override
        // 读取环境变量配置，并进行合并，优先级最高
        configs.updateConfigsNotNotify(updateEnvCfg());

        // 输出配置
        if (LOGGER.isDebugEnabled()) {
            final String display = configs.toPrettyDisplay();
            LOGGER.debug("Loaded conf:\n{}", display);
        }
        return configs;
    }

    private static GlobalConfigs loadDefaultConfigs(ClassLoader loader, String file) {
        GlobalConfigs globalConfigs = JarFileConfigLoader.load(file);
        if (globalConfigs != null) {
            return globalConfigs;
        }
        return ConfigLoader.loadFromClasspath(loader, file);
    }

}
