# Context

`Context` 在每次触发被拦截的方法时，都会被创建。其后被应用与 `Interceptor` 中。
其保存了执行期间的相关信息。

context 使用 put/remove/get 来操作上下文中的数据。

context 可以读取全局的配置信息。

context 还会记录某个对象进入了的次数。这对于某些不可重复触发的 Interceptor 非常有效

context 可以生成一个 Async context，对应跨线程时需要携带信息的场景非常有用。（线程池）

context 可以生成一个 Request context，对于跨 server 时需要携带信息的场景非常有用。（HTTP/Dubbo/Kafka/RabbitMQ）

context 可以生成一个 Message Request，这是对 Request context 的进一步封装，适用于消息队列的场景。（Kafka/RabbitMQ）

context 可以生成一个 Span，这对于分布式的链路追踪非常有用。

context 可以注入 Forwarded headers，这对于某些场景的增强非常有用。（HTTP）

## TracingContext

可以设置用于当前 session 的 tracing context。
