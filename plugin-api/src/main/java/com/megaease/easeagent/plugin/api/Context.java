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

package com.megaease.easeagent.plugin.api;

import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.api.context.AsyncContext;
import com.megaease.easeagent.plugin.api.context.RequestContext;
import com.megaease.easeagent.plugin.api.trace.*;
import com.megaease.easeagent.plugin.bridge.NoOpIPluginConfig;

/**
 * A Context remains in the session it was bound to until business finish.
 *
 * 上下文会一直保留在其会话中，直到业务结束
 */
@SuppressWarnings("unused")
public interface Context {
    /**
     * 如果为 true，该拦截器不应继续执行，并且不应上报任何内容。
     *
     * When true, do nothing and nothing is reported . However, this Context should
     * still be injected into outgoing requests. Use this flag to avoid performing expensive
     * computation.
     */
    boolean isNoop();

    /**
     * 返回最近使用的 tracing 组件，如果已经关闭的话，则返回 null
     *
     * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
     *
     * <p>This object should not be cached.
     */
    Tracing currentTracing();

    /**
     * 返回上下文中键对应的值，如果不存在或者显示设置了 null，则返回 null。
     *
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this context contains no mapping for the key.
     *
     * <p>More formally, if this context contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * <p>If this context permits null values, then a return value of
     * {@code null} does not <i>necessarily</i> indicate that the context
     * contains no mapping for the key; it's also possible that the context
     * explicitly maps the key to {@code null}.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     * {@code null} if this context contains no mapping for the key
     * @throws ClassCastException if the key is of an inappropriate type for
     *                            this context
     *                            (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    <V> V get(Object key);

    /**
     * 移除上下文中键对应的值，并返回该值对应的值，如果不存在或者显示设置了 null，则返回 null。
     *
     * Removes the mapping for a key from this Context if it is present
     * (optional operation).   More formally, if this context contains a mapping
     * from key <tt>k</tt> to value <tt>v</tt> such that
     * <code>(key==null ?  k==null : key.equals(k))</code>, that mapping
     * is removed.  (The context can contain at most one such mapping.)
     *
     * <p>Returns the value to which this context previously associated the key,
     * or <tt>null</tt> if the context contained no mapping for the key.
     *
     * <p>If this context permits null values, then a return value of
     * <tt>null</tt> does not <i>necessarily</i> indicate that the context
     * contained no mapping for the key; it's also possible that the context
     * explicitly mapped the key to <tt>null</tt>.
     *
     * <p>The context will not contain a mapping for the specified key once the
     * call returns.
     *
     * @param key key whose mapping is to be removed from the Context
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * @throws ClassCastException if the key is of an inappropriate type for
     *                            this context
     *                            (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    <V> V remove(Object key);

    /**
     * 向上下文中添加键值对，并返回该键之前对应的值，如果不存在或者显示设置了 null，则返回 null。
     *
     * Associates the specified value with the specified key in this context
     * (optional operation).  If the context previously contained a mapping for
     * the key, the old value is replaced by the specified value.  (A context
     * <tt>m</tt> is said to contain a mapping for a key <tt>k</tt>
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the context
     * previously associated <tt>null</tt> with <tt>key</tt>,
     * if the implementation supports <tt>null</tt> values.)
     * @throws ClassCastException if the class of the specified key or value
     *                            prevents it from being stored in this context
     */
    <V> V put(Object key, V value);

    /**
     * 读取配置
     *
     * Looks at the config at the current without removing it
     * from the stack.
     *
     * @return The config at the top of this stack (the last config of the <tt>Config</tt> object).
     * return {@link NoOpIPluginConfig#INSTANCE} if this stack is empty.
     */
    IPluginConfig getConfig();

    /**
     * 记录目标对象{@code key}的Session的堆叠序列，该方法必须配合 {@link #exit(Object)}方法。
     *
     * Record and return the stacking sequence of Object{@code key}'s Session
     * It needs to be used together with the {@link #exit(Object)} to be effective
     * for example 1:
     * <pre>{@code
     *      fun1(){
     *          try{
     *              if (context.enter(obj)!=1){
     *                 return;
     *              }
     *              //do something1
     *          }finally{
     *              if (context.exit(obj)!=1){
     *                 return;
     *              }
     *              //do something2
     *          }
     *      }
     *      fun2(){
     *          try{
     *              if (context.enter(obj)!=1){
     *                 return;
     *              }
     *              // call fun1();
     *              //do something3
     *          }finally{
     *              if (context.exit(obj)!=1){
     *                 return;
     *              }
     *              //do something4
     *          }
     *      }
     * }</pre>
     * if call fun2(), something1 and something2 will no longer execute
     * <p>
     * for example 2:
     *
     * <pre>{@code
     *      fun1(){
     *          try{
     *              if (context.enter(obj)>2){
     *                 return;
     *              }
     *              //do something1
     *          }finally{
     *              if (context.exit(obj)>2){
     *                 return;
     *              }
     *              //do something2
     *          }
     *      }
     *      fun2(){
     *          try{
     *              if (context.enter(obj)>2){
     *                 return;
     *              }
     *              // call fun1();
     *              //do something3
     *          }finally{
     *              if (context.exit(obj)>2){
     *                 return;
     *              }
     *              //do something4
     *          }
     *      }
     *      fun3(){
     *          try{
     *              if (context.enter(obj)>2){
     *                 return;
     *              }
     *              // call fun2();
     *              //do something5
     *          }finally{
     *              if (context.exit(obj)>2){
     *                 return;
     *              }
     *              //do something6
     *          }
     *      }
     * }</pre>
     * if call fun3(), something1 and something2 will no longer execute
     *
     * @param key the Object of stacking sequence
     * @return stacking sequence
     * @see #exit(Object)
     */
    int enter(Object key);

    /**
     * 记录并验证进入目标对象的次数是否符合预期值
     *
     * Record and verify the stacking sequence of Object{@code key}'s Session
     * It needs to be used together with the {@link #exit(Object, int)} to be effective
     *
     * @param key   the Object of stacking sequence
     * @param times the verify of stacking sequence
     * @return true if stacking sequence is {@code times} else false
     * @see #enter(Object)
     */
    default boolean enter(Object key, int times) {
        return enter(key) == times;
    }

    /**
     * 退出并返回目标对象{@code key}的Session的堆叠序列，该方法必须配合 {@link #enter(Object)}方法。
     *
     * Release and return the stacking sequence of Object{@code key}'s Session
     * It needs to be used together with the {@link #enter(Object)} to be effective
     *
     * @param key the Object of stacking sequence
     * @return stacking sequence
     * @see #enter(Object)
     */
    int exit(Object key);

    /**
     * 退出并验证进入目标对象的次数是否符合预期值
     *
     * Release and verify the stacking sequence of Object's Session
     * It needs to be used together with the {@link #enter(Object, int)} to be effective
     *
     * @param key   the Object of stacking sequence
     * @param times the verify of stacking sequence
     * @return true if stacking sequence is {@code times} else false
     * @see #exit(Object)
     */
    default boolean exit(Object key, int times) {
        return exit(key) == times;
    }


    //---------------------------------- 1. async context begin ------------------------------------------
    //---------------------------------- 1. Cross-thread 主要为跨线程的场景而使用 ------------------------------------------
    // When you import and export the AsyncContext, you will also import and export the Tracing context for Thread.

    /**
     * 创建一个 AsyncContext，其将 copy 当前上下文中所有的 key:value，需要注意仅赋值 Context#context, Context#supplier, Context#tracing#exportAsync
     *
     * Export a {@link AsyncContext} for async
     * It will copy all the key:value in the current Context
     *
     * @return {@link AsyncContext}
     */
    AsyncContext exportAsync();

    /**
     * 从 AsyncContext 导入到当前的上下文中，将 copy 目标上下文中所有的 key:value
     *
     * Import a {@link AsyncContext} for async
     * It will copy all the key: value to the current Context
     * <p>
     * If you don’t want to get the Context, you can use the {@link AsyncContext#importToCurrent()} proxy call
     * <p>
     * The Cleaner must be close after business:
     * <p>
     * example:
     * <pre>{@code
     *    void callback(Context context, AsyncContext ac){
     *       try (Cleaner cleaner = context.importAsync(ac)) {
     *          //do business
     *       }
     *    }
     * }</pre>
     *
     * @param snapshot the AsyncContext from {@link #exportAsync()} called
     * @return {@link Cleaner} for tracing
     */
    Cleaner importAsync(AsyncContext snapshot);

    /**
     * 包装一个 Runnable，本质上是创建一个 AsyncContext 放到该 Runnable 中。
     * 然后使用 CurrentContextRunnable 返回。
     *
     * Wraps the input so that it executes with the same context as now.
     */
    Runnable wrap(Runnable task);

    /**
     * 检查该 Task 是否已经被包装过了
     *
     * Check task is wrapped.
     *
     * @param task Runnable
     * @return true if task is warpped.
     */
    boolean isWrapped(Runnable task);
    //---------------------------------- 1. async context end ------------------------------------------


    //----------------------------------2. Cross-server ------------------------------------------

    /**
     * 为下一个 server 创建一个 RequestContext，其将传递多个 key:value 以满足 Trace。
     * 这发生在向 Dubbo/Motan/HTTP/Sofa/Kafka/RabbitMQ 等 Server 发起请求时
     *
     * 用于创建 Kind=CLIENT 的 span，此处会在之后向其他服务器发起调用
     *
     * Create a RequestContext for the next Server
     * It will pass multiple key:value values required by Trace and EaseAgent through
     * {@link Request#setHeader(String, String)}, And set the Span's kind, name and
     * cached scope through {@link Request#kind()}, {@link Request#name()} and {@link Request#cacheScope()}.
     * <p>
     * When you want to call the next Server, you can pass the necessary key:value to the next Server
     * by implementing {@link Request#setHeader(String, String)}, or you can get the {@link RequestContext} of return,
     * call {@link RequestContext#getHeaders()} to get it and pass it on.
     * <p>
     * It is usually called on the client request when collaboration between multiple server is required.
     * {@code client.clientRequest(Request.setHeader<spanId,root-source...>) --> server }
     * or
     * {@code client.clientRequest(Request).getHeaders<spanId,root-source...> --> server }
     * <p>
     * The Scope must be close after plugin:
     *
     * <pre>{@code
     *    void after(...){
     *       RequestContext rc = context.get(...)
     *       try{
     *
     *       }finally{
     *           rc.scope().close();
     *       }
     *    }
     * }</pre>
     *
     * @param request {@link Request}
     * @return {@link RequestContext}
     */
    RequestContext clientRequest(Request request);


    /**
     * 从上游的 Server 创建一个 RequestContext，其将传递多个 key:value 以满足 Trace。
     * 这发生在接收来自 Dubbo/Motan/HTTP/Sofa/Kafka/RabbitMQ 等 Server 的请求时
     *
     * 用于创建 Kind=SERVER 的 span，此处用于响应客户端的调用
     *
     * Obtain key:value from the request passed by a parent Server and create a RequestContext
     * <p>
     * It will not only obtain the key:value required by Trace from the {@link Request#header(String)},
     * but also other necessary key:value of EaseAgent, such as the key configured in the configuration file:
     * {@link ProgressFields#EASEAGENT_PROGRESS_FORWARDED_HEADERS_CONFIG}
     * <p>
     * If there is no Tracing Header, it will create a Root Span
     * <p>
     * It will set the Span's kind, name and cached scope through {@link Request#kind()}, {@link Request#name()}
     * and {@link Request#cacheScope()}.
     * <p>
     * It is usually called on the server receives a request when collaboration between multiple server is required.
     * {@code client --> server.serverReceive(Request<spanId,root-source...>) }
     * <p>
     * The Scope must be close after plugin:
     *
     * <pre>{@code
     *    void after(...){
     *       RequestContext rc = context.get(...)
     *       try{
     *
     *       }finally{
     *           rc.scope().close();
     *       }
     *    }
     * }</pre>
     *
     * @param request {@link Request}
     * @return {@link RequestContext}
     */
    RequestContext serverReceive(Request request);


    //---------------------------------- 3. Message Tracing ------------------------------------------

    /**
     * 从 message request 提取 key:value，并生成一个新的 span，例如：Kafka consumer, RabbitMQ consumer
     *
     * Obtain key:value from the message request and create a Span, Examples: kafka consumer, rabbitMq consumer
     * <p>
     * It will set the Span's kind, name and cached scope through {@link Request#kind()}, {@link Request#name()}
     * and {@link Request#cacheScope()}.
     *
     * <p>
     * It will set the Span's tags "messaging.operation", "messaging.channel_kind" and "messaging.channel_name" from request
     * {@link MessagingRequest#operation()} {@link MessagingRequest#channelKind()} {@link MessagingRequest#channelName()}
     *
     * <p>
     * It is usually called on the consumer.
     * {@code Kafka Server --> consumer.consumerSpan(Record<spanId,X-EG-Circuit-Breaker...>) }
     *
     * @param request {@link MessagingRequest}
     * @return {@link Span}
     */
    Span consumerSpan(MessagingRequest request);


    /**
     * 从 message request 提取 key:value，并生成一个新的 span，例如：Kafka producer, RabbitMQ producer
     *
     * Create a Span for message producer. Examples: kafka producer, rabbitMq producer
     * <p>
     * It will set the Span's tags "messaging.operation", "messaging.channel_kind", "messaging.channel_name" from request
     * {@link MessagingRequest#operation()} {@link MessagingRequest#channelKind()} {@link MessagingRequest#channelName()}
     * And set the Span's kind, name and cached scope through {@link Request#kind()}, {@link Request#name()} and
     * {@link Request#cacheScope()}.
     *
     * <p>
     * It will not only pass multiple key:value values required by Trace through {@link Request#setHeader(String, String)},
     * but also other necessary key:value of EaseAgent, such as the key configured in the configuration file:
     * {@link ProgressFields#EASEAGENT_PROGRESS_FORWARDED_HEADERS_CONFIG}
     * <p>
     * <p>
     * It is usually called on the producer.
     * {@code producer.producerSpan(Record) -- Record<spanId,root-source...> --> Message Server}
     *
     * @param request {@link MessagingRequest}
     * @return {@link Span}
     */
    Span producerSpan(MessagingRequest request);

    /**
     * 注入 Consumer Span 的 key:value 和 Forwarded Headers 到 Request {@link MessagingRequest#setHeader(String, String)}中。
     *
     * Inject Consumer's Span key:value and Forwarded Headers to Request {@link MessagingRequest#setHeader(String, String)}.
     *
     * @param span    key:value from
     * @param request key:value to
     * @see Request#setHeader(String, String)
     */
    void consumerInject(Span span, MessagingRequest request);

    /**
     * 注入 Producer Span 的 key:value 和 Forwarded Headers 到 Request {@link MessagingRequest#setHeader(String, String)}中。
     *
     * Inject Producer's Span and Forwarded Headers key:value to Request {@link MessagingRequest#setHeader(String, String)}.
     *
     * @param span    key:value from
     * @param request key:value to
     * @see Request#setHeader(String, String)
     */
    void producerInject(Span span, MessagingRequest request);


    //---------------------------------- 4. Span ------------------------------------------

    /**
     * 返回一个新的子 span，如果已经存在了 trace 则直接使用，否则生成一个新的 trace，然后生成一个新的 trace。
     *
     * Returns a new child span if there's a {@link Tracing#currentSpan()} or a new trace if there isn't.
     *
     * @return {@link Span}
     */
    Span nextSpan();

    /**
     * 返回该 key 是否是 Trace 组件必须的 key，如果是的话，则该 key:value 需要被传递到下一个 span 中，
     *
     * @return true if the key is necessary for EaseAgent
     */
    boolean isNecessaryKeys(String key);

    /**
     * 将 Forwarded Headers key:value 注入到 Setter 中。
     * 比如对于 Http 来说就是将 key:value 注入到 http header 中。
     *
     * Inject Forwarded Headers key:value to Setter {@link Setter#setHeader(String, String)}.
     *
     * @param setter key:value to
     * @see Request#setHeader(String, String)
     */
    void injectForwardedHeaders(Setter setter);

    /**
     * 从 Getter 中获取 Forwarded Headers key:value 并注入到 Context 中。
     *
     * Import Forwarded Headers key:value to Context {@link Getter#header(String)}.
     * <p>
     * The Cleaner must be close after plugin:
     *
     * <pre>{@code
     *    void after(...){
     *       Cleaner c = context.remove(...)
     *       try{
     *
     *       }finally{
     *           c.close();
     *       }
     *    }
     * }</pre>
     *
     * @param getter name from
     * @return {@link Scope} for current session
     * @see Request#header(String)
     */
    Cleaner importForwardedHeaders(Getter getter);
}
