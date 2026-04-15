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
 * 在 loader/Main 中，被 EaseAgentClassLoader 进行加载，但是其内部的 CLASSLOADER 却是独立的。
 */
@SuppressWarnings("all")
public class FinalClassloaderSupplier implements Supplier<ClassLoader> {
    // 该 ClassLoader 指向 easeagent.jar/log4j2 下单独的一个 class loader
    // 具体看 com.megaease.easeagent.Main#initEaseAgentSlf4j2Dir
    // 名称为 URLClassLoader
    public static volatile ClassLoader CLASSLOADER = null;


    @Override
    public ClassLoader get() {
        return CLASSLOADER;
    }
}
