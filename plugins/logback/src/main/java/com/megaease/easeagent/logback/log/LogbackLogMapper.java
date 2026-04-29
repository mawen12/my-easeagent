/*
 * Copyright (c) 2022, MegaEase
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
package com.megaease.easeagent.logback.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.api.otlp.common.AgentLogData;
import com.megaease.easeagent.plugin.api.otlp.common.AgentLogDataImpl;
import com.megaease.easeagent.plugin.api.otlp.common.LogMapper;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import io.opentelemetry.sdk.logs.data.Severity;

import java.util.Map;

/**
 * reference to opentelemetry logback instrumentation lib
 */
public class LogbackLogMapper implements LogMapper {
    // TODO replace by enum
    public static final LogbackLogMapper INSTANCE = new LogbackLogMapper();

    @Override
    public AgentLogData mapLoggingEvent(MethodInfo methodInfo, int levelInt, IPluginConfig config) {
        if (methodInfo.getArgs() == null || methodInfo.getArgs().length < 1) {
            return null;
        }

        // 读取首个参数
        ILoggingEvent loggingEvent = (ILoggingEvent)methodInfo.getArgs()[0];
        // 读取日志级别
        Level level = loggingEvent.getLevel();
        if (level == null || level.levelInt < levelInt) {
            return null;
        }

        AgentLogDataImpl.Builder builder = AgentLogDataImpl.builder();
        // logger 读取 logger
        String logger = loggingEvent.getLoggerName();
        if (logger == null || logger.isEmpty()) {
            logger = "ROOT";
        }
        builder.logger(logger);
        // message 读取消息
        String message = loggingEvent.getFormattedMessage();
        if (message != null) {
            builder.body(message);
        }

        // time 读取时间戳
        long timestamp = loggingEvent.getTimeStamp();
        builder.epochMills(timestamp);

        // level 写入日志级别
        builder.severity(levelToSeverity(level));
        builder.severityText(level.levelStr);

        // throwable 处理异常
        Object throwableProxy = loggingEvent.getThrowableProxy();
        Throwable throwable = null;
        if (throwableProxy instanceof ThrowableProxy) { // 处理异常包装的情况
            // there is only one other subclass of ch.qos.logback.classic.spi.
            // IThrowableProxy and it is only used for logging exceptions over the wire
            throwable = ((ThrowableProxy) throwableProxy).getThrowable();
        }
        if (throwable != null) {
            builder.throwable(throwable);
        }

        Thread currentThread = Thread.currentThread();
        // 写入线程
        builder.thread(currentThread);

        // MDC 写入 MDC
        Map<String, String> contextData = loggingEvent.getMDCPropertyMap();
        builder.contextData(config.getStringList(LogMapper.MDC_KEYS), contextData);

        // span context 写入 span
        builder.spanContext();

        return builder.build();
    }

    private static Severity levelToSeverity(Level level) {
        switch (level.levelInt) {
            case Level.ALL_INT:
            case Level.TRACE_INT:
                return Severity.TRACE;
            case Level.DEBUG_INT:
                return Severity.DEBUG;
            case Level.INFO_INT:
                return Severity.INFO;
            case Level.WARN_INT:
                return Severity.WARN;
            case Level.ERROR_INT:
                return Severity.ERROR;
            case Level.OFF_INT:
            default:
                return Severity.UNDEFINED_SEVERITY_NUMBER;
        }
    }
}
