# Metric

度量和监控系统的性能指标，有吞吐量、延迟、错误率等。

使用 Prometheus 的客户端 和 micrometer 来收集指标。

使用 Prometheus 的 server 来保存指标。

使用 Prometheus 的 HTTP 来上传指标。

使用 Grafana 来展示 Prometheus 的数据。

## Report

1. 从 Bootstrap 中获取 BeanProvider 实现，即从 Bootstrap#loadProvider 开始，找到 `MetricBeanProviderImpl`。
2. 初始化 `MetricBeanProviderImpl` 时，内部会初始化 `MetricProviderImpl`。
3. 将 `MetricBeanProviderImpl` 注册到 `ContextManager#setMetric` 中，本质上是将 `MetricProviderImpl#ApplicationMetricRegistrySupplier` 注册到 `ContextManager#metric` 中，
4. 然后再将其传播到 `EaseAgent.metricRegistrySupplier` 上
5. `ServiceMetricRegistry#getOrCreate` 每当对应的 

### 上报流程

在异步的 `AgentScheduledReporter` 定时调用 `report` 方法。
1. `report` 方法检查是否开启了 `enabled` 配置，没有开启则直接返回。
2. `report` 方法将所有指标借助 `converter` 转换为通用的 `List<Map<String,Object>>` 格式。
3. `report` 方法将转换后的数据使用 `encoder` 进行编码
4. `report` 方法将编码后的数据使用 `AgentKafkaSender/AgentLoggerSender/NoOpSender/MetricKafkaSender/HttpSender` 进行上报。

## 设计

### Metric 

顶层的设计接口，代表指标。

### Counter

支持自增和自减的计数器，适用于计数事件的发生次数。

底层使用 `io.dropwizard.metrics:metrics-core` 的 `Counter` 实现。

###  Meter

衡量平均吞吐量的指标，支持一分钟、五分钟、15分钟的平均吞吐量。

底层使用 `io.dropwizard.metrics:metrics-core` 的 `Meter` 实现。

### Timer

计时器指标，汇总事件的持续时间，提供平均时间、最大时间、最小时间等统计信息。

底层使用 `io.dropwizard.metrics:metrics-core` 的 `Timer` 实现。

### Histogram

计算值分布的指标。

底层使用 `io.dropwizard.metrics:metrics-core` 的 `Histogram` 实现。


## 配置

指标的相关配置主要由 `MetricsConfig` 和 `PluginMetricsConfig` 实现。

配置项：
```properties
enabled: true // 是否开启指标上报


```
