/*
 * Copyright (c) 2021 MegaEase
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

package com.megaease.easeagent.plugin.jdbc.common;

import com.megaease.easeagent.plugin.api.config.Config;
import com.megaease.easeagent.plugin.bridge.EaseAgent;

public class SQLCompressionWrapper implements SQLCompression {

    public static final SQLCompressionWrapper INSTANCE = new SQLCompressionWrapper();

    private static final String SQL_COMPRESS_ENABLED = "plugin.observability.jdbc.sql.compress.enabled";

    @Override
    public String compress(String origin) {
        // 读取配置
        Config config = EaseAgent.getConfig();
        // 检查是否开启了 SQL 压缩
        Boolean enabled = config.getBoolean(SQL_COMPRESS_ENABLED);
        // 如果开启了，则使用 MD5 的 SQL 压缩方案
        if (enabled) {
            return MD5SQLCompression.getInstance().compress(origin);
        }
        // 否则使用默认的方案，即不压缩
        return SQLCompression.DEFAULT.compress(origin);
    }
}
