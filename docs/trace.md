# Trace

分布式追踪的实现。

## 实现

使用 zipkin 的客户端 brave 和 OpenTelemetry 收集日志。

使用 zipkin 的服务端 zipkin-server 和 Kafka 来存储日志。

使用 zipkin 的 HTTP/Kafka 来上传日志。

使用 zipkin 的 ui 来展示数据。

## 业务实现

- 单线程
- 多线程
- client -> server
  - http
    - OpenFeign
    - RestTemplate
  - dubbo
  - sofa
  - motan
- producer -> consumer
  - kafka
  - rabbitmq

### 基本
