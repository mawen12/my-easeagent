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
import com.megaease.easeagent.plugin.api.config.Const;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.api.config.PluginConfigChangeListener;
import com.megaease.easeagent.plugin.utils.common.StringUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PluginConfig implements IPluginConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginConfig.class);
    private final Set<PluginConfigChangeListener> listeners;
    private final String domain;
    private final String namespace;
    private final String id;
    // 全局配置
    private final Map<String, String> global;
    // 覆盖配置，即特定该插件的配置
    private final Map<String, String> cover;
    // 配置是否开启
    private final boolean enabled;

    protected PluginConfig(@Nonnull String domain, @Nonnull String id, @Nonnull Map<String, String> global, @Nonnull String namespace, @Nonnull Map<String, String> cover, @Nonnull Set<PluginConfigChangeListener> listeners) {
        this.domain = domain;
        this.namespace = namespace;
        this.id = id;
        this.global = global;
        this.cover = cover;
        this.listeners = listeners;

        // 读取 enabled 配置，如果没有配置，则默认为 false
        Boolean b = getBoolean(Const.ENABLED_CONFIG);
        if (b == null) {
            enabled = false;
        } else {
            enabled = b;
        }
    }

    public static PluginConfig build(@Nonnull String domain, @Nonnull String id,
                                     @Nonnull Map<String, String> global, @Nonnull String namespace,
                                     @Nonnull Map<String, String> cover, PluginConfig oldConfig) {
        Set<PluginConfigChangeListener> listeners;
        if (oldConfig == null) {
            listeners = new HashSet<>();
        } else {
            listeners = oldConfig.listeners;
        }
        return new PluginConfig(domain, id, global, namespace, cover, listeners);
    }

    @Override
    public String domain() {
        return domain;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public String id() {
        return id;
    }


    @Override
    public boolean hasProperty(String property) {
        return global.containsKey(property) || cover.containsKey(property);
    }

    @Override
    public String getString(String property) {
        // 从 cover 读取配置
        String value = cover.get(property);
        if (value != null) {
            return value;
        }

        // fallback 从 global 读取配置
        return global.get(property);
    }


    @Override
    public Integer getInt(String property) {
        // conver -> global
        String value = this.getString(property);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTrue(String value) {
        return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
    }

    @Override
    public Boolean getBoolean(String property) {
        // 从 cover 中读取配置
        String value = cover.get(property);
        boolean implB = true;
        if (value != null) {
            implB = isTrue(value);
        }

        // 从 global 中读取配置
        value = global.get(property);
        boolean globalB = false;
        if (value != null) {
            globalB = isTrue(value);
        }

        // 取两者的与，即只有当 cover 中配置为 true 且 global 中配置为 true 时，才返回 true，否则返回 false
        return implB && globalB;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Double getDouble(String property) {
        // conver -> global
        String value = this.getString(property);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Long getLong(String property) {
        // conver -> global
        String value = this.getString(property);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> getStringList(String property) {
        String value = this.getString(property);
        if (StringUtils.isEmpty(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .collect(Collectors.toList());
    }

    @Override
    public IPluginConfig getGlobal() {
        return new Global(domain, id, global, namespace);
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = new HashSet<>(global.keySet());
        keys.addAll(cover.keySet());
        return keys;
    }

    @Override
    public void addChangeListener(PluginConfigChangeListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void foreachConfigChangeListener(Consumer<PluginConfigChangeListener> action) {
        Set<PluginConfigChangeListener> oldListeners;
        synchronized (listeners) {
            oldListeners = new HashSet<>(listeners);
        }
        for (PluginConfigChangeListener oldListener : oldListeners) {
            try {
                action.accept(oldListener);
            } catch (Exception e) {
                LOGGER.error("PluginConfigChangeListener<{}> change plugin config fail : {}", oldListener.getClass(), e.getMessage());
            }
        }
    }

    public class Global extends PluginConfig implements IPluginConfig {

        public Global(String domain, String id, Map<String, String> global, String namespace) {
            super(domain, id, global, namespace, Collections.emptyMap(), Collections.emptySet());
        }

        @Override
        public void addChangeListener(PluginConfigChangeListener listener) {
            PluginConfig.this.addChangeListener(listener);
        }

        @Override
        public void foreachConfigChangeListener(Consumer<PluginConfigChangeListener> action) {
            PluginConfig.this.foreachConfigChangeListener(action);
        }
    }
}
