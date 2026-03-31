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

package com.megaease.easeagent.context;

import com.megaease.easeagent.config.Configs;
import com.megaease.easeagent.plugin.api.ProgressFields;
import com.megaease.easeagent.plugin.api.config.ChangeItem;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ProgressFieldsManager {

    private ProgressFieldsManager() {
    }

    // init 将配置
    public static void init(Configs configs) {
        // 负责监听配置变化，并将变化的配置更新到 ProgressFields 中.
        Consumer<Map<String, String>> changeListener = ProgressFields.changeListener();
        // 初始化时将当前的配置更新到 ProgressFields 中.
        changeListener.accept(configs.getConfigs());
        // 向当前配置注册监听器，当显式触发配置更新时，更新 ProgressFields 中的配置.
        configs.addChangeListener(list -> {
            Map<String, String> map = new HashMap<>();
            for (ChangeItem changeItem : list) {
                String key = changeItem.getFullName();
                // 过滤出与 ProgressFields 相关的配置项，并将其更新到 ProgressFields 中.
                if (ProgressFields.isProgressFields(key)) {
                    map.put(key, changeItem.getNewValue());
                }
                // 更新 ProgressFields 中的配置项
                changeListener.accept(map);
            }
        });
    }
}
