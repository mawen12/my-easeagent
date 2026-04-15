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
package com.megaease.easeagent.plugin.report.tracing;

import java.util.List;
import java.util.Map;

/**
 * 用于上报给 zipkin 的数据定义。
 * 内部收集的时候是 span，此处需要转换为 zipkin 支持的数据模型
 * 详见：https://zipkin.io/pages/data_model.html
 * https://zipkin.io/zipkin-api/#/
 *
 * borrow form zipkin2.Span
 */
@SuppressWarnings("unused")
public interface ReportSpan {
    /**
     * span base
     *
     * minLength: 16 64bits
     * maxLength: 32 128bits
     *
     * 随机生成，用于标识一个 trace（包含多个 span）的唯一性
     */
    String traceId();

    //

    /**
     * 当其本身是 root span 时，该值为 null
     *
     * minLength: 16 64bits
     * maxLength: 16 64bits
     */
    String parentId();

    /**
     * spanId
     *
     * 用于标识当前 span 的唯一性，随机生成
     */
    String id();

    /**
     * Span.Kind.name
     *
     * 用于明确时间戳、耗时、远程对端信息。如果没有提供，则被视为本地span或尚未完成。
     */
    String kind();

    /**
     * 代表一个逻辑操作，以小写字母提供
     *
     * Span name in lowercase, rpc method for example.
     *
     * <p>Conventionally, when the span name isn't known, name = "unknown".
     */
    String name();

    /**
     * 该 span 开始的 epoch 微秒数，如果是未完成的 span，则为0。
     *
     * 存在0的情况：
     * - 该 span 已被创建，但是尚未开始
     * - 该 span 的开始事件丢失
     * - 已完成的 span 在上报后设置的。
     *
    * Epoch microseconds of the start of this span, possibly zero if this an incomplete span.
    */
    long timestamp();

    /**
     * 关键路径的微秒级耗时，对于小于1的值，将向上舍入到1微秒。
     * 在异步场景中，子级的持续时间可能超过父级的持续时间。
     *
    * Measurement in microseconds of the critical path, if known. Durations of less than one
    * microsecond must be rounded up to 1 microsecond.
    */
    long duration();

    /**
     * 如果为另一个服务上的 span 创建提供了某些数据，就设置为 true。
     *
     * True if we are contributing to a span started by another tracer (ex on a different host).
     * Defaults to null. When set, it is expected for {@link #kind()} to be Kind#SERVER}.
     */
    boolean shared();

    /**
     * 如果为 true，则请求存储此 span，即使它覆盖了采样策略。
     *
     * True is a request to store this span even if it overrides sampling policy.
     */
    boolean debug();

    /**
     * 记录当前 app 的信息
     *
    * The host that recorded this span, primarily for query by service name.
    */
    Endpoint localEndpoint();

    /**
     * 记录跨进程通信的对端 app 的信息，比如访问数据库时，记录数据库的信息；访问其他服务时，记录被访问服务的信息
     *
    * The host that recorded this span, primarily for query by service name.
    */
    Endpoint remoteEndpoint();

    /**
     * 与事件发生时相关的时间戳和描述
     *
     * annotation
     */
    List<Annotation> annotations();

    /**
     * 可以被分析、查看和检索
     *
     * tags
     */
    Map<String, String> tags();

    String tag(String key);

    default boolean hasError() {
        return tags().containsKey("error");
    }

    default String errorInfo() {
        return tags().get("error");
    }

    /**
     * global
     */
    String type();
    String service();
    String system();

    String localServiceName();
    String remoteServiceName();
}
