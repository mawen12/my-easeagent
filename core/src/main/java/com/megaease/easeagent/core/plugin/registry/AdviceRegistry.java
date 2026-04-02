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

import com.megaease.easeagent.core.plugin.matcher.MethodTransformation;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentAdvice.Dispatcher;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentAdvice.OffsetMapping;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentJavaConstantValue;
import com.megaease.easeagent.core.plugin.transformer.advice.MethodIdentityJavaConstant;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.interceptor.AgentInterceptorChain;
import com.megaease.easeagent.plugin.utils.common.WeakConcurrentMap;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.StackManipulation;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class AdviceRegistry {

    private AdviceRegistry() {
    }

    private static final ThreadLocal<WeakReference<ClassLoader>> CURRENT_CLASS_LOADER = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(AdviceRegistry.class);
    static Map<String, PointcutsUniqueId> methodsSet = new ConcurrentHashMap<>();

    public static Integer check(TypeDescription instrumentedType,
                                MethodDescription instrumentedMethod,
                                Dispatcher.Resolved.ForMethodEnter methodEnter,
                                Dispatcher.Resolved.ForMethodExit methodExit) {
        // 读取类名
        String clazz = instrumentedType.getName();
        // 读取方法名
        String method = instrumentedMethod.getName();
        // 读取方法描述符
        String methodDescriptor = instrumentedMethod.getDescriptor();
        // 构造一个 key
        String key = clazz + ":" + method + methodDescriptor;

        // 缓存起来
        PointcutsUniqueId newIdentity = new PointcutsUniqueId();
        PointcutsUniqueId pointcutsUniqueId = methodsSet.putIfAbsent(key, newIdentity);

        Integer pointcutIndex;
        boolean merge = false;

        // already exist
        // 如果已经存在了
        if (pointcutsUniqueId != null) {
            // 进行释放
            newIdentity.tryRelease();
            // 读取 pointcut 索引
            pointcutIndex = getPointcutIndex(methodEnter);
            // this pointcut's interceptors have injected into chain
            // 如果已经设置过值
            if (pointcutsUniqueId.checkPointcutExist(pointcutIndex)) {
                // 检查是否设置过 class loader，设置过无需要再增强了
                if (pointcutsUniqueId.checkClassloaderExist()) {
                    // don't need to instrumented again.
                    return 0;
                } else {
                    /*
                     * Although the interceptor of the pointcut has been injected,
                     * the method of this class owned by current loader has not been instrumented
                     */
                    // 该方法未被增强
                    updateStackManipulation(methodEnter, pointcutsUniqueId.getUniqueId());
                    updateStackManipulation(methodExit, pointcutsUniqueId.getUniqueId());
                    return pointcutsUniqueId.getUniqueId();
                }
            } else {
                // Orchestration
                merge = true;
            }
        // 之前没有，这是新的
        } else {
            // new
            pointcutsUniqueId = newIdentity;
            //
            pointcutIndex = updateStackManipulation(methodEnter, pointcutsUniqueId.getUniqueId());
            updateStackManipulation(methodExit, pointcutsUniqueId.getUniqueId());
        }

        // merge or registry
        MethodTransformation methodTransformation = PluginRegistry.getMethodTransformation(pointcutIndex);
        if (methodTransformation == null) {
            log.error("MethodTransformation get fail for {}", pointcutIndex);
            return 0;
        }
        int uniqueId = pointcutsUniqueId.getUniqueId();
        AgentInterceptorChain chain = methodTransformation
            .getAgentInterceptorChain(uniqueId, clazz, method, methodDescriptor);

        try {
            pointcutsUniqueId.lock();
            AgentInterceptorChain previousChain = com.megaease.easeagent.core.plugin.Dispatcher.getChain(uniqueId);
            if (previousChain == null) {
                // 注册拦截器链
                com.megaease.easeagent.core.plugin.Dispatcher.register(uniqueId, chain);
            } else {
                // 合并之前的拦截器
                chain.merge(previousChain);
                com.megaease.easeagent.core.plugin.Dispatcher.updateChain(uniqueId, chain);
            }
        } finally {
            pointcutsUniqueId.unlock();
        }

        if (merge) {
            return 0;
        }

        return uniqueId;
    }

    static Integer getPointcutIndex(Dispatcher.Resolved resolved) {
        int index = 0;
        Map<Integer, OffsetMapping> enterMap = resolved.getOffsetMapping();
        for (Map.Entry<Integer, OffsetMapping> offset : enterMap.entrySet()) {
            OffsetMapping om = offset.getValue();
            if (!(om instanceof OffsetMapping.ForStackManipulation)) {
                continue;
            }
            OffsetMapping.ForStackManipulation forStackManipulation = (OffsetMapping.ForStackManipulation) om;
            if (!(forStackManipulation.getStackManipulation() instanceof AgentJavaConstantValue)) {
                continue;
            }

            AgentJavaConstantValue value = (AgentJavaConstantValue) forStackManipulation.getStackManipulation();
            index = value.getPointcutIndex();
            break;
        }
        return index;
    }

    static Integer updateStackManipulation(Dispatcher.Resolved resolved, Integer value) {
        int index = 0;
        Map<Integer, OffsetMapping> enterMap = resolved.getOffsetMapping();

        for (Map.Entry<Integer, OffsetMapping> offset : enterMap.entrySet()) {
            OffsetMapping om = offset.getValue();
            if (!(om instanceof OffsetMapping.ForStackManipulation)) {
                continue;
            }
            OffsetMapping.ForStackManipulation forStackManipulation = (OffsetMapping.ForStackManipulation) om;
            if (!(forStackManipulation.getStackManipulation() instanceof AgentJavaConstantValue)) {
                continue;
            }

            AgentJavaConstantValue oldValue = (AgentJavaConstantValue) forStackManipulation.getStackManipulation();
            index = oldValue.getPointcutIndex();

            MethodIdentityJavaConstant constant = new MethodIdentityJavaConstant(value);
            StackManipulation stackManipulation = new AgentJavaConstantValue(constant, index);
            enterMap.put(offset.getKey(), forStackManipulation.with(stackManipulation));

            return index;
        }
        return index;
    }

    public static void setCurrentClassLoader(ClassLoader loader) {
        CURRENT_CLASS_LOADER.set(new WeakReference<>(loader));
    }

    public static ClassLoader getCurrentClassLoader() {
        return CURRENT_CLASS_LOADER.get().get();
    }

    public static void cleanCurrentClassLoader() {
        CURRENT_CLASS_LOADER.remove();
    }

    private static class PointcutsUniqueId {
        static AtomicInteger index = new AtomicInteger(1);
        ReentrantLock lock = new ReentrantLock();
        int uniqueId;
        ConcurrentHashMap<Integer, Integer> pointcutIndexSet = new ConcurrentHashMap<>();
        WeakConcurrentMap<ClassLoader, Boolean> cache = new WeakConcurrentMap<>();

        public PointcutsUniqueId() {
            // 初始持有一个全局的index递增后的id
            this.uniqueId = index.incrementAndGet();
        }

        // 检查 pointcutIndexSet 是否设置过值，如果没有则设置一个
        public boolean checkPointcutExist(Integer pointcutIndex) {
            return this.pointcutIndexSet.putIfAbsent(pointcutIndex, pointcutIndex) != null;
        }

        public int getUniqueId() {
            return this.uniqueId;
        }

        public boolean checkClassloaderExist() {
            ClassLoader loader = getCurrentClassLoader();
            return cache.putIfProbablyAbsent(loader, true) != null;
        }

        public void lock() {
            this.lock.lock();
        }

        public void unlock() {
            this.lock.unlock();
        }

        /**
         * Some empty slots may appear, the effect can be ignored and can be optimized later
         */
        public void tryRelease() {
            int id = this.uniqueId;
            index.compareAndSet(id, id - 1);
        }
    }
}
