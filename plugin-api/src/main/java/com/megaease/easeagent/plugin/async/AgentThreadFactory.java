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

package com.megaease.easeagent.plugin.async;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义的线程工厂，线程名称为 EaseAgent-<x>，且仅使用守护线程
 */
public class AgentThreadFactory implements ThreadFactory {
    protected static AtomicInteger createCount = new AtomicInteger(1);					// Used internally to compute Thread names that comply with the Java specification

    @Override
    public Thread newThread(@Nullable Runnable r) {
        // 线程名称为：EaseAgent-<x>
        Thread thread = new Thread(r, "EaseAgent-" + createCount.getAndIncrement());
        // 守护线程，不会影响JVM的正常关闭
        thread.setDaemon(true);
        return thread;
    }
}
