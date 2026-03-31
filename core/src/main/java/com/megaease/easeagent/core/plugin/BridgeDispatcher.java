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

import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.InitializeContext;
import com.megaease.easeagent.plugin.api.dispatcher.IDispatcher;
import com.megaease.easeagent.plugin.bridge.EaseAgent;

public class BridgeDispatcher implements IDispatcher {
    // enter
    @Override
    public void enter(int chainIndex, MethodInfo info) {
        // 获取上下文
        InitializeContext context = EaseAgent.initializeContextSupplier
            .getContext();
        // 如果上下文为空，则直接退出
        if (context.isNoop()) {
            return;
        }
        // 调用分发器的enter方法
        Dispatcher.enter(chainIndex, info, context);
    }

    // exit
    @Override
    public Object exit(int chainIndex, MethodInfo methodInfo,
                     Context context, Object result, Throwable e) {
        // 如果上下文为空，或者上下文不是 InitializeContext 类型，直接返回
        if (context.isNoop() || !(context instanceof InitializeContext)) {
            return result;
        }
        InitializeContext iContext = (InitializeContext)context;
        // 记录异常
        methodInfo.throwable(e);
        // 记录返回值
        methodInfo.retValue(result);
        // 调用分发器的 exit 方法
        Dispatcher.exit(chainIndex, methodInfo, iContext);
        // 如果方法信息发生了变化，则返回方法信息中的返回值，否则返回原来的返回值
        if (methodInfo.isChanged()) {
            result = methodInfo.getRetValue();
        }

        return result;
    }
}
