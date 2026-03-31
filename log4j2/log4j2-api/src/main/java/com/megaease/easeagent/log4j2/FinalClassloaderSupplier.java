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

package com.megaease.easeagent.log4j2;

import java.util.function.Supplier;

/**
 * 该类会在 Main.java 中使用 EaseAgent class loader进行加载，
 * 然后会给 CLASSLOADER 赋值，值为 agent jar 下的 log4j2/ 目录下的所有 jar 为搜索范围，且没有父级的的 class loader.
 */
@SuppressWarnings("all")
public class FinalClassloaderSupplier implements Supplier<ClassLoader> {
    // 该 ClassLoader 指向 agent jar/log4j2 下所有 jar 单独的一个 class loader
    public static volatile ClassLoader CLASSLOADER = null;


    @Override
    public ClassLoader get() {
        return CLASSLOADER;
    }
}
