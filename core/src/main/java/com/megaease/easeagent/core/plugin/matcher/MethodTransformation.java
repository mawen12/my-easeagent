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

package com.megaease.easeagent.core.plugin.matcher;

import com.megaease.easeagent.core.plugin.Dispatcher;
import com.megaease.easeagent.core.plugin.interceptor.InterceptorPluginDecorator;
import com.megaease.easeagent.core.plugin.interceptor.ProviderChain;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.api.config.ConfigConst;
import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.interceptor.Interceptor;
import com.megaease.easeagent.plugin.Ordered;
import com.megaease.easeagent.plugin.interceptor.AgentInterceptorChain;
import lombok.Data;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher.Junction;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Data
@SuppressWarnings("unused")
public class MethodTransformation {
    private static final Logger log = LoggerFactory.getLogger(MethodTransformation.class);

    // 该值用于之后从 com.megaease.easeagent.core.plugin.registry.PluginRegistry.INTERCEPTOR_PROVIDERS 获取对应的 Interceptor
    private int index;
    // 经过转换后的 byte buddy Junction<MethodDescription>，本质是 ease agent 的 IMethodMatcher
    private Junction<? super MethodDescription> matcher;
    // index 对应的 InterceptorProvider 链构建器，用于之后获取 InterceptorProvider 链
    private ProviderChain.Builder providerBuilder;

    public MethodTransformation(int index,
                                Junction<? super MethodDescription> matcher,
                                ProviderChain.Builder chain) {
        this.index = index;
        this.matcher = matcher;
        this.providerBuilder = chain;
    }

    // easeagent 此处做了优化，但是对于普通的 byte buddy 插件来说，可以在获取该 agent chain 时进行调用。
    // private static final ConcurrentMap<Interger, InterceptorChain> CHAINS = new ConcurrentHashMap<>();
    // private static final ConcurrentMap<Integer, List<Supplier<Interceptor>>> REGISRTERED = new ConcurrentHashMap<>();
    //
    // public static void register(int uniqueIndex, List<Supplier<Interceptor>> suppliers) {
    //      REGISRTERED.put(uniqueIndex, suppliers);
    // }
    //
    // public static InterceptorChain getOrCreateChain(int uniqueIndex, String className, String methodName, String methodDescriptor, IPluginConfig) {
    //      return CHAINS.computeIfAbsent(uniqueIndex, index -> {
    //          List<Supplier<Interceptor>> suppliers = REGISTER.getOrDefault(idx, List.of());
    //
    //          List<Interceptor> interceptors = suppliers.stream().map(Supplier::get).sorted(Comparator.comparing(Ordered::order)).collect(Collectors.toList());
    //
    //          for (Interceptor i : interceptors) {
    //             try {
    //               i.init(config, className, methodName, methodDescriptor);
    //             } catch(Exception e) {
    //               //...
    //            }
    //          }
    //         return new InterceptorChain(interceptors);
    //      })
    // }
    public AgentInterceptorChain getAgentInterceptorChain(final int uniqueIndex,
                                                          final String type,
                                                          final String method,
                                                          final String methodDescription) {
        // 获取 InterceptorProvider 链
        List<Supplier<Interceptor>> suppliers = this.providerBuilder.build()
            .getSupplierChain();

        // 按照 Ordered 排序
        List<Interceptor> interceptors = suppliers.stream()
            .map(Supplier::get)
            .sorted(Comparator.comparing(Ordered::order))
            .collect(Collectors.toList());

        interceptors.forEach(i -> {
            InterceptorPluginDecorator interceptor;
            if (i instanceof InterceptorPluginDecorator) {
                interceptor = (InterceptorPluginDecorator) i;
                try {
                    interceptor.init(interceptor.getConfig(), type, method, methodDescription);
                    interceptor.init(interceptor.getConfig(), uniqueIndex);
                } catch (Exception e) {
                    log.error("Interceptor init fail: {}::{}, {}", type, method, interceptor.getClass().getSimpleName());
                }
            }
        });

        return new AgentInterceptorChain(interceptors);
    }
}
