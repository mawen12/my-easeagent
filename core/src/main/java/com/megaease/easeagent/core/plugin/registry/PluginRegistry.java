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

package com.megaease.easeagent.core.plugin.registry;

import com.google.common.base.Strings;
import com.megaease.easeagent.core.plugin.interceptor.ProviderChain;
import com.megaease.easeagent.core.plugin.interceptor.ProviderChain.Builder;
import com.megaease.easeagent.core.plugin.interceptor.ProviderPluginDecorator;
import com.megaease.easeagent.core.plugin.matcher.*;
import com.megaease.easeagent.core.utils.AgentArray;
import com.megaease.easeagent.plugin.AgentPlugin;
import com.megaease.easeagent.plugin.Points;
import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.interceptor.InterceptorProvider;
import com.megaease.easeagent.plugin.matcher.IClassMatcher;
import com.megaease.easeagent.plugin.matcher.IMethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PluginRegistry {
    static Logger log = EaseAgent.getLogger(PluginRegistry.class);

    // 保存 InterceptorProvider#getAdviceTo -> AgentPlugin 的映射
    static final ConcurrentHashMap<String, AgentPlugin> QUALIFIER_TO_PLUGIN = new ConcurrentHashMap<>();
    // 保存
    static final ConcurrentHashMap<String, AgentPlugin> POINTS_TO_PLUGIN = new ConcurrentHashMap<>();
    // 保存所有的 AgentPlugin 实现
    static final ConcurrentHashMap<String, AgentPlugin> PLUGIN_CLASSNAME_TO_PLUGIN = new ConcurrentHashMap<>();
    // 保存满足CodeVersion的 Points 实现
    static final ConcurrentHashMap<String, Points> POINTS_CLASSNAME_TO_POINTS = new ConcurrentHashMap<>();
    // 保存 InterceptorProvider#getAdviceTo -> index 的映射，index 用于在 ProviderChain 中获取对应的 InterceptorProvider
    static final ConcurrentHashMap<String, Integer> QUALIFIER_TO_INDEX = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer, MethodTransformation> INDEX_TO_METHOD_TRANSFORMATION = new ConcurrentHashMap<>();
    // 保存拦截器提供者
    static final AgentArray<Builder> INTERCEPTOR_PROVIDERS = new AgentArray<>();

    private PluginRegistry() {
    }

    // register 注册 AgentPlugin
    public static void register(AgentPlugin plugin) {
        PLUGIN_CLASSNAME_TO_PLUGIN.putIfAbsent(plugin.getClass().getCanonicalName(), plugin);
    }

    // register 注册 Points
    public static void register(Points points) {
        POINTS_CLASSNAME_TO_POINTS.putIfAbsent(points.getClass().getCanonicalName(), points);
    }

    public static Collection<Points> getPoints() {
        return POINTS_CLASSNAME_TO_POINTS.values();
    }

    public static Points getPoints(String pointsClassName) {
        return POINTS_CLASSNAME_TO_POINTS.get(pointsClassName);
    }

    private static String getMethodQualifier(String classname, String qualifier) {
        return classname + ":" + qualifier;
    }

    // registerClassTransformation 从 Points 中解析 ClassLoaderMatcher/ClassMatcher/MethodMatcher 并生成 ClassTransformation
    public static ClassTransformation registerClassTransformation(Points points) {
        // 获取 Points 的规范类名
        String pointsClassName = points.getClass().getCanonicalName();
        // 获取 Points 的类匹配器
        IClassMatcher classMatcher = points.getClassMatcher();
        // 获取 Points 是否需要添加动态字段
        boolean hasDynamicField = points.isAddDynamicField();
        // 将类匹配器转换为 ByteBuddy 的 Junction<TypeDescription>
        Junction<TypeDescription> innerClassMatcher = ClassMatcherConvert.INSTANCE.convert(classMatcher);
        // 将类加载器匹配器转换为 ByteBuddy 的 ElementMatcher<ClassLoader>
        ElementMatcher<ClassLoader> loaderMatcher = ClassLoaderMatcherConvert.INSTANCE
            .convert(points.getClassLoaderMatcher());

        // 获取 Points 的方法匹配器集合
        Set<IMethodMatcher> methodMatchers = points.getMethodMatcher();

        // 处理方法匹配器
        Set<MethodTransformation> mInfo = methodMatchers.stream().map(matcher -> {
            // 将方法匹配器转换为 ByteBuddy 的 Junction<MethodDescription>
            Junction<MethodDescription> bMethodMatcher = MethodMatcherConvert.INSTANCE.convert(matcher);
            // 拼接全限定方法名称
            String qualifier = getMethodQualifier(pointsClassName, matcher.getQualifier());
            // 获取 qualifier 对应的 index，如果没有则返回 null
            Integer index = QUALIFIER_TO_INDEX.get(qualifier);
            if (index == null) {
                // it is unusual for this is a pointcut without interceptor.
                // maybe there is some error in plugin providers configuration
                return null;
            }
            // 获取 index 对应的 InterceptorProvider.Builder，如果没有则返回 null
            Builder providerBuilder = INTERCEPTOR_PROVIDERS.get(index);
            if (providerBuilder == null) {
                return null;
            }
            // 创建 MethodTransformation
            MethodTransformation mt = new MethodTransformation(index, bMethodMatcher, providerBuilder);
            // 注册 index -> MethodTransformation 映射，如果已经存在则记录错误日志
            if (INDEX_TO_METHOD_TRANSFORMATION.putIfAbsent(index, mt) != null) {
                log.error("There are duplicate qualifier in Points:{}!", qualifier);
            }
            return mt;
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        // 获取该 Points 对应的 AgentPlugin，读取其执行顺序
        AgentPlugin plugin = POINTS_TO_PLUGIN.get(pointsClassName);
        int order = plugin.order();
        // 构造 ClassTransformation
        return ClassTransformation.builder()
            // 来源于 Points#classMatcher
            .classMatcher(innerClassMatcher)
            // 来源于 Points#hasDyanmicField
            .hasDynamicField(hasDynamicField)
            // 来源于 Points#methodMatcher
            .methodTransformations(mInfo)
            // 来源于 Points#classloaderMatcher
            .classloaderMatcher(loaderMatcher)
            // 来源 Points#typeFieldAccessor
            .typeFieldAccessor(points.getTypeFieldAccessor())
            // 来源于 AgentPlugin#order
            .order(order)
            .build();
    }

    // register 注册拦截器提供者
    public static int register(InterceptorProvider provider) {
        // 获取要被增强的方法名称
        String qualifier = provider.getAdviceTo();
        // map interceptor/pointcut to plugin
        // 读取该拦截器提供者对应的插件实现类名
        AgentPlugin plugin = PLUGIN_CLASSNAME_TO_PLUGIN.get(provider.getPluginClassName());
        if (plugin == null) {
            // code autogenerate issues that are unlikely to occur!
            // 出现这种问题，只能是代码生成出现了问题
            throw new RuntimeException();
        }
        // 注册 qualifier -> plugin 映射
        QUALIFIER_TO_PLUGIN.putIfAbsent(qualifier, plugin);
        // 注册 points class name -> plugin 映射
        POINTS_TO_PLUGIN.putIfAbsent(getPointsClassName(qualifier), plugin);

        // generate index and supplier chain
        // 获取该拦截器提供者对应的 index，如果没有则创建一个新的 index，并将该 index 与 qualifier 进行映射
        Integer index = QUALIFIER_TO_INDEX.get(provider.getAdviceTo());
        if (index == null) {
            synchronized (QUALIFIER_TO_INDEX) {
                // 再次获取，防止已经有了
                index = QUALIFIER_TO_INDEX.get(provider.getAdviceTo());
                if (index == null) {
                    // 注册一个预占位
                    index = INTERCEPTOR_PROVIDERS.add(ProviderChain.builder());
                    // 注册 qualifier -> index 映射
                    QUALIFIER_TO_INDEX.putIfAbsent(provider.getAdviceTo(), index);
                }
            }
        }
        // 注册 index -> InterceptorProvider 映射
        INTERCEPTOR_PROVIDERS.get(index)
            .addProvider(new ProviderPluginDecorator(plugin, provider));

        return index;
    }

    // getPointsClassName 从中提取 point class name
    // 去除 : 及其后面的内容
    public static String getPointsClassName(String name) {
        int index;
        if (Strings.isNullOrEmpty(name)) {
            return "unknown";
        }
        index = name.indexOf(':');
        if (index < 0) {
            return name;
        }
        return name.substring(0, index);
    }

    public static MethodTransformation getMethodTransformation(int pointcutIndex) {
        return INDEX_TO_METHOD_TRANSFORMATION.get(pointcutIndex);
    }

    public static void addMethodTransformation(int pointcutIndex, MethodTransformation info) {
        INDEX_TO_METHOD_TRANSFORMATION.putIfAbsent(pointcutIndex, info);
    }

}
