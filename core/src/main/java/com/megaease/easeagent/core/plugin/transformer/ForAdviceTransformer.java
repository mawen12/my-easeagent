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

import com.megaease.easeagent.core.plugin.CommonInlineAdvice;
import com.megaease.easeagent.core.plugin.annotation.Index;
import com.megaease.easeagent.core.plugin.matcher.MethodTransformation;
import com.megaease.easeagent.core.plugin.registry.AdviceRegistry;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentAdvice;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentAdvice.OffsetMapping;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentForAdvice;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentJavaConstantValue;
import com.megaease.easeagent.core.plugin.transformer.advice.MethodIdentityJavaConstant;
import com.megaease.easeagent.core.plugin.transformer.classloader.CompoundClassloader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.utility.JavaModule;

/**
 * 单个方法级别的 Transformer，从 Points#methodMatcher -> MethodTransformation -> ForAdviceTransformer
 * 该类是使用 byte buddy 最核心的地方
 * 用于在 EaseAgent 中动态修改类的字节码，以便插入自定义的逻辑
 */
public class ForAdviceTransformer implements AgentBuilder.Transformer {

    private final AgentForAdvice transformer;
    private final MethodTransformation methodTransformInfo;

    public ForAdviceTransformer(MethodTransformation methodTransformInfo) {
        this.methodTransformInfo = methodTransformInfo;

        // 用于从 PluginRegistry#INTERCEPTOR_PROVIDERS 获取对应的 Interceptor，转换为 JavaConstant
        MethodIdentityJavaConstant value = new MethodIdentityJavaConstant(methodTransformInfo.getIndex());
        // 将 methodTransformInfo 的索引值作为常量字段写入 @Index 索引指定的参数上
        StackManipulation stackManipulation = new AgentJavaConstantValue(value, methodTransformInfo.getIndex());
        //
        TypeDescription typeDescription = value.getTypeDescription();

        // 绑定自定义的 OffsetMapping，此处用于在增强逻辑中绑定额外的参数，比如 Index 注解
        OffsetMapping.Factory<Index> factory = new OffsetMapping.ForStackManipulation.Factory<>(Index.class,
            stackManipulation,
            typeDescription.asGenericType());

        // 使用 AgentForAdvice 来定义增强逻辑，并将其绑定到匹配的方法上
        this.transformer = new AgentForAdvice(AgentAdvice.withCustomMapping()
            .bind(factory))
            // 使用该类加载的 ClassLoader
            .include(getClass().getClassLoader())
            // 定义了需要增强的方法匹配规则
            .advice(methodTransformInfo.getMatcher(), // Points#MethodMatcher
                // 指定了增强的实现类
                CommonInlineAdvice.class.getCanonicalName());
    }

    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b, TypeDescription td, ClassLoader cl, JavaModule m) {
        // 将传入的 ClassLoader 加入到 EaseAgentClassLoader
        CompoundClassloader.compound(this.getClass().getClassLoader(), cl);

        // 使用静态变量保存传入的 ClassLoader
        AdviceRegistry.setCurrentClassLoader(cl);
        // 使用自定义的 transformer 进行转换
        DynamicType.Builder<?> bd = transformer.transform(b, td, cl, m);
        // 清除静态变量中的 ClassLoader
        AdviceRegistry.cleanCurrentClassLoader();

        return bd;
    }
}
