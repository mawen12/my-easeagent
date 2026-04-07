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

package com.megaease.easeagent.metrics.config;

import java.util.concurrent.TimeUnit;

/**
 * metrics 相关的配置，只有是否开启，上报的间隔
 */
public interface MetricsConfig {
    boolean isEnabled();

    int getInterval();

    TimeUnit getIntervalUnit();

    void setIntervalChangeCallback(Runnable runnable);
}
