/*
 * Copyright (c) 2021, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent.log4j2;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * 用于对外提供 ClassLoader 的接口。主要从 FinalClassloaderSupplier#CLASSLOADER -> ServiceLoader 获取
 */
public interface ClassloaderSupplier {

    ClassLoader get();

    class ClassloaderSupplierImpl implements ClassloaderSupplier {

        @Override
        public ClassLoader get() {
            // 尝试直接从 FinalClassloaderSupplier#CLASSLOADER 中获取 Classloader
            // 该类会在 Main.java 中使用 EaseAgent class loader 进行加载，并赋值
            // 正常执行过程中， 肯定会有 class loader，但是在某些场景中，比如测试，就可能需要通过第二个方式来获取
            FinalClassloaderSupplier supplier = new FinalClassloaderSupplier();
            ClassLoader classLoader = supplier.get();
            if (classLoader != null) {
                return classLoader;
            }
            // 尝试通过 ServiceLoader 加载 ClassloaderSupplier 的实现类，并获取 Classloader
            // ServiceLoader 会读取 META-INF/services/com.megaease.easeagent.log4j2.ClassloaderSupplier 文件，加载其中指定的实现类
            ServiceLoader<ClassloaderSupplier> loader = ServiceLoader.load(ClassloaderSupplier.class);
            Iterator<ClassloaderSupplier> iterator = loader.iterator();
            while (iterator.hasNext()) {
                classLoader = iterator.next().get();
                if (classLoader != null) {
                    return classLoader;
                }
            }
            return null;
        }
    }
}
