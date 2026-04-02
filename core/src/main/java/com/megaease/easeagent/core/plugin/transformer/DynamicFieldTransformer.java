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

package com.megaease.easeagent.core.plugin.transformer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.megaease.easeagent.core.plugin.transformer.DynamicFieldAdvice.DynamicInstanceInit;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.field.DynamicFieldAccessor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 向目标类添加字段，且实现接口。然后在构造器初始化的时候，将其值初始为默认的 NullObject
 */
public class DynamicFieldTransformer implements AgentBuilder.Transformer {
    private static final Logger log = LoggerFactory.getLogger(DynamicFieldTransformer.class);
    private static final ConcurrentHashMap<String, Cache<ClassLoader, Boolean>> FIELD_MAP = new ConcurrentHashMap<>();

    // 要添加的字段名
    private final String fieldName;
    // 字段的访问器
    private final Class<?> accessor;
    private final AgentBuilder.Transformer.ForAdvice transformer;

    public DynamicFieldTransformer(String fieldName) {
        this(fieldName, DynamicFieldAccessor.class);
    }

    public DynamicFieldTransformer(String fieldName, Class<?> accessor) {
        this.fieldName = fieldName;
        this.accessor = accessor;
        this.transformer = new AgentBuilder.Transformer
            .ForAdvice(Advice.withCustomMapping())
            // 指定查找该类时，使用该类的 Class Loader，确保能找到该类
            .include(getClass().getClassLoader())
            // 对目标类的构造其，应用 DynamicInstanceInit
            .advice(ElementMatchers.isConstructor(), DynamicInstanceInit.class.getName());
    }

    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b,
                                            TypeDescription td, ClassLoader cl, JavaModule m) {
        // 如果没有添加过，则添加该字段
        if (check(td, this.accessor, cl) && this.fieldName != null) {
            try {
                // 定义一个 private Object ease_agent_dynamic_$$$_data 字段
                b = b.defineField(this.fieldName, Object.class, Opcodes.ACC_PRIVATE)
                    // 该类实现了 DynamicFieldAccessor 接口
                    .implement(this.accessor)
                    // 使用 FieldAccessor 来实现 DynamicFieldAccessor 中的方法，因为其会根据 fieldName 生成 getter/setter 方法
                    .intercept(FieldAccessor.ofField(this.fieldName));
            } catch (Exception e) {
                log.debug("Type:{} add extend field again!", td.getName());
            }
            return transformer.transform(b, td, cl, m);
        }
        return b;
    }

    /**
     * 避免重复添加字段，因为可能存在多个Points指向同一个Class的情况，如果不进行去重检查，则会报错
     *
     * Avoiding add a accessor interface to a class repeatedly
     *
     * @param td       represent the class to be enhanced
     * @param accessor access interface class
     * @param cl       current classloader
     * @return return true when it is the first time
     */
    private static boolean check(TypeDescription td, Class<?> accessor, ClassLoader cl) {
        String key = td.getCanonicalName() + accessor.getCanonicalName();

        // 从缓存中读取
        Cache<ClassLoader, Boolean> checkCache = FIELD_MAP.get(key);
        if (checkCache == null) {
            // 创建一个缓存
            Cache<ClassLoader, Boolean> cache = CacheBuilder.newBuilder().weakKeys().build();
            if (cl == null) {
                // 读取当前线程的 class loader
                cl = Thread.currentThread().getContextClassLoader();
            }
            // 将其设值到缓存中
            cache.put(cl, true);
            // 在放入到 map 中
            checkCache = FIELD_MAP.putIfAbsent(key, cache);
            // 如果之前没有，则代表之前未设置过
            if (checkCache == null) {
                return true;
            }
        }

        // 二次检查该class loader 中是否有设置过
        return checkCache.getIfPresent(cl) == null;
    }
}
