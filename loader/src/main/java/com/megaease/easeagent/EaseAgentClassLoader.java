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

package com.megaease.easeagent;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * EaseAgent's exclusive classloader, used to isolate classes
 *
 * EaseAgent 的排他类加载器，用于隔离类。
 *
 * 在 EaseAgent 中，该类负责定位并加载 Agent jar 中被提取出来的 lib/, plugins/ 和 log4j2/ 路径，以及 agent.jar 中 plugins/ 下 jar 的路径中的类。
 *
 * 该 class loader 同时还提供了一个 add 方法，用于回退查找。
 */
public class EaseAgentClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Set<WeakReference<ClassLoader>> externals = new CopyOnWriteArraySet<>();

    public EaseAgentClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    // add 回退加载的 ClassLoader，当 loadClass 无法从当前 class loader 中加载类时，会回退到 externals 中的 class loader 进行加载
    // 实际上会由 CompoundClassloader 加入到 external 中
    @SuppressWarnings("unused")
    public void add(ClassLoader cl) {
        if (cl != null && !Objects.equals(cl, this)) {
            // TODO by mawen 此处存在一个问题，那就是即使 cl 是一个，但是因为每次创建不同的 Reference 对象，因此会重复添加
            // 而且原始使用了 CopyOnWriteArraySet 实现并发安全
            externals.add(new WeakReference<>(cl));
        }
    }

    // loadClass 用于定位并加载类
    // 先调用父类的 loadClass 方法，如果父类无法加载，则遍历 externals 中的 ClassLoader 进行加载
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            // 调用父类，此处是从之前提供的 url 中进行查找类
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            // 当类没有找到时，尝试从 externals 中的 ClassLoader 进行加载
            for (WeakReference<ClassLoader> external : externals) {
                try {
                    // 由于是 WeakReference，所以在使用前需要先检查是否已经被 gc 了
                    ClassLoader cl = external.get();
                    // 如果已经被 gc 了，则跳过
                    if (cl == null) {
                        continue;
                    }
                    // 尝试加载类
                    final Class<?> aClass = cl.loadClass(name);
                    // 尝试解析类
                    if (resolve) {
                        resolveClass(aClass);
                    }
                    // 成功则退出
                    return aClass;
                } catch (ClassNotFoundException ignored) {
                    // ignored
                }
            }

            throw e;
        }
    }


    // findResource 用于定位并读取资源
    // 先调用父类的 findResource 方法，如果父类无法找到资源，则遍历 externals 中的 ClassLoader 进行查找
    @Override
    public URL findResource(String name) {
        // 调用父类，此处是从之前提供的 url 中查找资源
        URL url = super.findResource(name);
        if (url == null) {
            // 当 url 没有找到时，尝试从 externals 中的 ClassLoader 进行查找
            for (WeakReference<ClassLoader> external : externals) {
                try {
                    ClassLoader cl = external.get();
                    // TODO 此处是不是缺少了一个 null 判断？如果 cl 已经被 gc 了，则跳过
                    url = cl.getResource(name);
                    if (url != null) {
                        return url;
                    }
                } catch (Exception ignored) {
                    // ignored
                }
            }
        }
        return url;
    }
}
