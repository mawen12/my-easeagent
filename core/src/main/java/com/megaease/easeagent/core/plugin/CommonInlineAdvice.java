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

package com.megaease.easeagent.core.plugin;

import com.megaease.easeagent.core.plugin.annotation.Index;
import com.megaease.easeagent.core.plugin.transformer.advice.AgentAdvice.NoExceptionHandler;
import com.megaease.easeagent.plugin.api.InitializeContext;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * uniform interceptor entrance
 * get interceptor chain thought index generated when transform
 * <p>
 * 统一的拦截器入口
 * 获取在 transform 阶段生成的拦截器链索引
 */
// suppress all warnings for the code at these warnings is intentionally written this way
@SuppressWarnings("all")
public class CommonInlineAdvice {
    private static final String CONTEXT = "easeagent_context";
    private static final String POS = "easeagent_pos";

    @Advice.OnMethodEnter(suppress = NoExceptionHandler.class)
    public static MethodInfo enter(@Index int index,
                                   @Advice.This(optional = true) Object invoker,
                                   @Advice.Origin("#t") String type, // 类型
                                   @Advice.Origin("#m") String method, // 方法
                                   // readOnly = false + DYNAMIC 代表可以对参数进行修改，
                                   @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args, // 参数
                                   @Advice.Local(CONTEXT) InitializeContext context // 上下文
    ) {
        // 此处返回的是实际是 ContextManager
        context = EaseAgent.initializeContextSupplier.getContext();
        // 如果 Context 设置为 Noop，则不会触发后续的 interceptor chain
        if (context.isNoop()) {
            return null;
        }

        // 构造方法信息，主要是把传递过来的方法调用信息保存
        MethodInfo methodInfo = MethodInfo.builder()
            .invoker(invoker)
            .type(type)
            .method(method)
            .args(args)
            .build();
        // 使用 interceptor chain 执行
        Dispatcher.enter(index, methodInfo, context);
        // 更新方法参数
        if (methodInfo.isChanged()) {
            args = methodInfo.getArgs();
        }

        // 返回
        return methodInfo;
    }

    // 当有异常时抛出
    @Advice.OnMethodExit(onThrowable = Exception.class, suppress = NoExceptionHandler.class)
    // @Advice.OnMethodExit(suppress = NoExceptionHandler.class)
    public static void exit(@Index int index,
                            @Advice.Enter MethodInfo methodInfo,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result, // 方法执行结果
                            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwable, // 方法执行抛出的异常
                            @Advice.Local(CONTEXT) InitializeContext context) {
        // 如果 Context 设置为 Noop，则不会触发后续的 interceptor chain
        if (context.isNoop()) {
            return;
        }
        // 记录异常
        methodInfo.throwable(throwable);
        // 记录返回值
        methodInfo.retValue(result);
        // 执行 exit 调用
        Dispatcher.exit(index, methodInfo, context);
        // 更新返回值
        if (methodInfo.isChanged()) {
            result = methodInfo.getRetValue();
        }
    }

    // 正常方法退出
    @Advice.OnMethodExit(suppress = NoExceptionHandler.class)
    public static void exit(@Index int index,
                            @Advice.This(optional = true) Object invoker,
                            @Advice.Enter MethodInfo methodInfo,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result,
                            @Advice.Local(CONTEXT) InitializeContext context) {
        if (context.isNoop()) {
            return;
        }
        methodInfo.setInvoker(invoker);
        methodInfo.retValue(result);
        Dispatcher.exit(index, methodInfo, context);
        if (methodInfo.isChanged()) {
            result = methodInfo.getRetValue();
        }
    }
}
