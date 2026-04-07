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
 *
 */
package com.megaease.easeagent.report.plugin;

import com.megaease.easeagent.plugin.api.config.Config;
import com.megaease.easeagent.plugin.report.Encoder;
import com.megaease.easeagent.plugin.report.Sender;
import com.megaease.easeagent.report.sender.NoOpSender;
import com.megaease.easeagent.report.sender.SenderConfigDecorator;
import com.megaease.easeagent.report.sender.SenderWithEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.megaease.easeagent.config.report.ReportConfigConst.APPEND_TYPE_KEY;
import static com.megaease.easeagent.config.report.ReportConfigConst.join;

public class ReporterRegistry {
    static Logger logger = LoggerFactory.getLogger(ReporterRegistry.class);

    static ConcurrentHashMap<String, Supplier<Encoder<?>>> encoders = new ConcurrentHashMap<>();
    /**
     * kafka -> com.megaease.easeagent.report.sender.AgentKafkaSender
     * console -> com.megaease.easeagent.report.sender.AgentLoggerSender
     * noop -> com.megaease.easeagent.report.sender.NoOpSender
     * metricKafka -> com.megaease.easeagent.report.sender.metric.MetricKafkaSender
     * http -> com.megaease.easeagent.report.sender.okhttp.HttpSender
     */
    static ConcurrentHashMap<String, Supplier<Sender>> senderSuppliers = new ConcurrentHashMap<>();

    private ReporterRegistry() {}

    public static void registryEncoder(String name, Supplier<Encoder<?>> encoder) {
        Supplier<Encoder<?>> o = encoders.putIfAbsent(name, encoder);
        if (o != null) {
            String on = o.get().getClass().getSimpleName();
            String cn = encoder.get().getClass().getSimpleName();
            logger.error("Encoder name conflict:{}, between {} and {}", name, on, cn);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Encoder<T> getEncoder(String name) {
        if (encoders.get(name) == null) {
            logger.error("Encoder name \"{}\" is not exists!", name);
            return (Encoder<T>) NoOpEncoder.INSTANCE;
        }
        Encoder<T> encoder = (Encoder<T>)encoders.get(name).get();

        if (encoder == null) {
            return (Encoder<T>)NoOpEncoder.INSTANCE;
        }

        return encoder;
    }

    public static void registrySender(String name, Supplier<Sender> sender) {
        Supplier<Sender> o = senderSuppliers.putIfAbsent(name, sender);
        if (o != null) {
            String on = o.get().getClass().getSimpleName();
            String cn = sender.get().getClass().getSimpleName();
            logger.error("Sender name conflict:{}, between {} and {}", name, on, cn);
        }
    }

    public static SenderWithEncoder getSender(String prefix, Config config) {
        // 读取配置的 appendType，获取其上报类型
        String name = config.getString(join(prefix, APPEND_TYPE_KEY));
        if (name == null) {
            logger.warn("Can not find sender name for:{}", join(prefix, APPEND_TYPE_KEY));
        }
        // 根据上报类型获取 Sender 实例，并使用 SenderConfigDecorator 包装，传入配置项
        SenderWithEncoder sender = new SenderConfigDecorator(prefix, getSender(name), config);
        sender.init(config, prefix);
        return sender;
    }

    // 根据上报类型获取 Sender 实例，如果不存在则返回 NoOpSender
    // 支持 console/kafka/metricKafka/http/noop 等上报类型
    private static Sender getSender(String name) {
        Supplier<Sender> supplier = senderSuppliers.get(name);
        if (supplier == null) {
            return NoOpSender.INSTANCE;
        }
        return supplier.get();
    }
}
