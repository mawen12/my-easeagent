/*
 * Copyright (c) 2017, MegaEase
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

package com.megaease.easeagent;

import com.megaease.easeagent.core.Bootstrap;

import java.lang.instrument.Instrumentation;

/**
 * 该类会在 Main.java 中使用 EaseAgent class loader进行加载。
 * 该类被配置在 META-INF/MANIFEST.MF 中的 Premain-Class 属性中，作为 java agent 的 premain 方法入口。
 * 加载该类的Thread使用 ease agent class loader，且系统属性中使用agent的日志配置，覆盖了host的日志配置。
 */
public class StartBootstrap {
    private StartBootstrap() {}

    /**
     * @param args Main 的方法参数
     * @param inst Main 的方法参数
     * @param javaAgentJarPath ease agent jar 的路径
     */
    public static void premain(String args, Instrumentation inst, String javaAgentJarPath) {
        Bootstrap.start(args, inst, javaAgentJarPath);
    }
}
