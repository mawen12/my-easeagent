/*
 * Copyright (c) 2021, MegaEase
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

package com.megaease.easeagent.core.plugin.transformer.classloader;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.megaease.easeagent.core.plugin.matcher.MethodTransformation;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;

/**
 * 该类存在的意义在于为了能够访问到目标 JVM 中的类，比如 Redis 这些客户端使用的类，或者是用户自己定义的类，
 * 这些类不在 Agent 的 Classpath 中，所以需要通过 CompoundClassloader 将这些类添加到 Agent 的 Classpath 中
 */
public class CompoundClassloader {
    private static final Logger log = LoggerFactory.getLogger(MethodTransformation.class);
    private static final Cache<ClassLoader, Boolean> CACHE = CacheBuilder.newBuilder().weakKeys().build();


    public static boolean checkClassloaderExist(ClassLoader loader) {
        if (CACHE.getIfPresent(loader) == null) {
            CACHE.put(loader, true);
            return false;
        }
        return true;
    }

    public static ClassLoader compound(ClassLoader parent, ClassLoader external) {
        // 如果 external 为 null，或者 CACHE 中已经存在了，则直接返回
        if (external == null || checkClassloaderExist(external)) {
            return parent;
        }

        try {
            // 将 external 添加到 parent 中，本质上，该 parent 实际上为 EaseAgentClassLoader，因为只有该类才有 add 方法
            parent.getClass().getDeclaredMethod("add", ClassLoader.class).invoke(parent, external);
        } catch (Exception e) {
            log.warn("{}, this may be a bug if it was running in production", e.toString());
        }
        return parent;
    }
}
