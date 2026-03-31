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

package com.megaease.easeagent.core;

import com.megaease.easeagent.config.*;
import com.megaease.easeagent.context.ContextManager;
import com.megaease.easeagent.core.config.*;
import com.megaease.easeagent.core.info.AgentInfoFactory;
import com.megaease.easeagent.core.plugin.BaseLoader;
import com.megaease.easeagent.core.plugin.BridgeDispatcher;
import com.megaease.easeagent.core.plugin.PluginLoader;
import com.megaease.easeagent.httpserver.nano.AgentHttpHandlerProvider;
import com.megaease.easeagent.httpserver.nano.AgentHttpServer;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.api.config.ConfigConst;
import com.megaease.easeagent.plugin.api.metric.MetricProvider;
import com.megaease.easeagent.plugin.api.middleware.RedirectProcessor;
import com.megaease.easeagent.plugin.api.trace.TracingProvider;
import com.megaease.easeagent.plugin.bean.AgentInitializingBean;
import com.megaease.easeagent.plugin.bean.BeanProvider;
import com.megaease.easeagent.plugin.bridge.AgentInfo;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.report.AgentReport;
import com.megaease.easeagent.plugin.utils.common.StringUtils;
import com.megaease.easeagent.report.AgentReportAware;
import com.megaease.easeagent.report.DefaultAgentReport;
import lombok.SneakyThrows;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * 该类是由 Main.java 在使用 EaseAgent class loader 加载 StartBootstrap，
 * 然后直接调用该类的 start 方法，注意此时的 Thread 使用的是 EaseAgent class loader，
 * 且系统属性中使用agent的日志配置，覆盖了host的日志配置。
 */
@SuppressWarnings("unused")
public class Bootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);

    // easeagent.server.port
    private static final String AGENT_SERVER_PORT_KEY = ConfigFactory.AGENT_SERVER_PORT;
    // easeagent.server.enabled
    private static final String AGENT_SERVER_ENABLED_KEY = ConfigFactory.AGENT_SERVER_ENABLED;

    private static final String AGENT_MIDDLEWARE_UPDATE = "easeagent.middleware.update";

    private static final int DEF_AGENT_SERVER_PORT = 9900;

    static final String MX_BEAN_OBJECT_NAME = "com.megaease.easeagent:type=ConfigManager";

    private static ContextManager contextManager;

    private Bootstrap() {
    }

    @SneakyThrows
    public static void start(String args, Instrumentation inst, String javaAgentJarPath) {
        // 开始计时
        long begin = System.nanoTime();
        // 将 agent jar 的路径写入到系统属性中 easeagent.jar.path
        System.setProperty(ConfigConst.AGENT_JAR_PATH, javaAgentJarPath);

        // add bootstrap classes
        // 通过 ByteBuddy 将 META-INF/services/com.megaease.easeagent.plugin.AppendBootstrapLoader 中的类注入到 bootstrap class loader 中，完成类的注入
        Set<String> bootstrapClassSet = AppendBootstrapClassLoaderSearch.by(inst, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP);
        // 输出注入类的信息
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Injected class: {}", bootstrapClassSet);
        }

        // initiate configuration
        // 从系统属性或环境变量中获取配置文件路径，key从 easeagent.config.path -> otel.javaagent.configuration-file
        String configPath = ConfigFactory.getConfigPath();
        // 如果没有配置，则使用方法参数
        if (StringUtils.isEmpty(configPath)) {
            configPath = args;
        }

        ClassLoader classLoader = Bootstrap.class.getClassLoader();
        // 基于当前 agent jar 的版本，创建一个 AgentInfo
        final AgentInfo agentInfo = AgentInfoFactory.loadAgentInfo(classLoader);
        // 设值到 EaseAgent
        EaseAgent.agentInfo = agentInfo;
        // 读取 agent.properties 和 agent.yaml 文件的配置，再读取自定义的路径配置，然后进行合并
        final GlobalConfigs conf = ConfigFactory.loadConfigs(configPath, classLoader);
        // 将全局配置和类加载器封装到 WrappedConfigManager 中，并注册到 MBean 中，完成配置的暴露和管理
        wrapConfig(conf);

        // loader check
        // 将 ease agent class loader 设置到 GlobalAgentHolder 中，供全局使用
        GlobalAgentHolder.setAgentClassLoader((URLClassLoader) Bootstrap.class.getClassLoader());
        // 暴露给 EaseAgent 使用
        EaseAgent.agentClassloader = GlobalAgentHolder::getAgentClassLoader;

        // init Context/API
        // 基于配置初始化 ContextManager，主要核心是：配置，插件管理器，日志工厂，Mdc
        contextManager = ContextManager.build(conf);
        // 构建分发器
        EaseAgent.dispatcher = new BridgeDispatcher();

        // initInnerHttpServer
        // 初始化一个 http server，注册支持配置变更的路由，并按配置决定启动该 http server
        initHttpServer(conf);

        // redirection
        // 初始化中间件枚举类，主要是读取并解析对应的中间件配置
        RedirectProcessor.INSTANCE.init();

        // reporter
        // 加载所需的 encoder 和 sender,提取 report 配置，创建 AgentReport 实例
        final AgentReport agentReport = DefaultAgentReport.create(conf);
        // 设置到 GlobalAgentHolder 中，供全局使用
        GlobalAgentHolder.setAgentReport(agentReport);
        // 暴露给 EaseAgent 使用
        EaseAgent.agentReport = agentReport;

        // load plugins
        // 初始化 bytebuddy 的 AgentBuilder，设置相关策略，以及忽略的类名
        AgentBuilder builder = getAgentBuilder(conf, false);
        // 读取用于进行增强的 Plugin, Points, InterceptorProvider,并生成 ClassTransformation，然后进行增强
        builder = PluginLoader.load(builder, conf);

        // provider & beans
        // 通过 ServiceLoader 加载 BeanProvider，然后进行初始化
        loadProvider(conf, agentReport);

        long installBegin = System.currentTimeMillis();
        // 将 transformer 真正挂到 JVM ，开始对目标类生效
        builder.installOn(inst);
        LOGGER.info("installBegin use time: {}ms", (System.currentTimeMillis() - installBegin));

        LOGGER.info("Initialization has took {}ns", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
    }

    // initHttpServer 初始化一个 http server，注册支持配置变更的路由，并按配置决定启动该 http server
    private static void initHttpServer(Configs conf) {
        // inner httpserver
        // 读取配置 系统属性（easeagent.server.port） -> 配置项（easeagent.server.port） -> 默认值（9090）
        Integer port = conf.getInt(AGENT_SERVER_PORT_KEY);
        if (port == null) {
            port = DEF_AGENT_SERVER_PORT;
        }
        String portStr = System.getProperty(AGENT_SERVER_PORT_KEY, String.valueOf(port));
        port = Integer.parseInt(portStr);

        // 初始化 http server
        AgentHttpServer agentHttpServer = new AgentHttpServer(port);

        // 读取配置 easeagent.server.enabled
        boolean httpServerEnabled = conf.getBoolean(AGENT_SERVER_ENABLED_KEY);
        // 如果配置了启动，则启动 http server，并输出日志
        if (httpServerEnabled) {
            agentHttpServer.startServer();
            LOGGER.info("start agent http server on port:{}", port);
        }
        // 保存到全局的Agent中
        GlobalAgentHolder.setAgentHttpServer(agentHttpServer);

        // add httpHandler
        // 注册 http 路由
        // /config，负责更新 service 配置
        agentHttpServer.addHttpRoute(new ServiceUpdateAgentHttpHandler());
        // /config-canary，负责更新 canary 配置
        agentHttpServer.addHttpRoute(new CanaryUpdateAgentHttpHandler());
        // /config-global-transmission,其会更新 easeagent.progress.forwarded.headers.global.transmission. 配置
        agentHttpServer.addHttpRoute(new CanaryListUpdateAgentHttpHandler());
        // /plugins/domains/:domain/namespaces/:namespace/:id/properties/:property/:value/:version,其会更新插件属性配置
        agentHttpServer.addHttpRoute(new PluginPropertyHttpHandler());
        // /plugins/domains/:domain/namespaces/:namespace/:id/properties，其会更新查询属性配置
        agentHttpServer.addHttpRoute(new PluginPropertiesHttpHandler());
    }

    // loadProvider 通过 ServiceLoader 加载 BeanProvider，然后进行初始化
    private static void loadProvider(final Configs conf, final AgentReport agentReport) {
        // 使用 ServiceLoader 从 META-INF/services/com.megaease.easeagent.plugin.bean.BeanProvider 读取文件内容
        // 具体位于 easeagent.jar/lib 中，其实现有：
        // com.megaease.easeagent.metrics.MetricBeanProviderImpl
        // com.megaease.easeagent.metrics.jvm.JvmBeanProvider
        // com.megaease.easeagent.zipkin.TracingProviderImpl
        // com.megaease.easeagent.core.health.HealthProvider
        // com.megaease.easeagent.core.info.AgentInfoProvider
        List<BeanProvider> providers = BaseLoader.loadOrdered(BeanProvider.class);
        // 对 BeanProvider 进行按接口回调，并初始化 contextManager
        providers.forEach(input -> provider(input, conf, agentReport));
    }

    // provider 对 BeanProvider 进行按接口回调，并初始化 contextManager
    private static void provider(final BeanProvider beanProvider, final Configs conf, final AgentReport agentReport) {
        // 当实现了 ConfigAware 接口，设置配置
        if (beanProvider instanceof ConfigAware) {
            ((ConfigAware) beanProvider).setConfig(conf);
        }
        // 当实现了 AgentReportAware 接口，设置report
        if (beanProvider instanceof AgentReportAware) {
            ((AgentReportAware) beanProvider).setAgentReport(agentReport);
        }

        // 当实现了 AgentHttpHandlerProvider 接口，添加 http 路由
        if (beanProvider instanceof AgentHttpHandlerProvider) {
            GlobalAgentHolder.getAgentHttpServer()
                .addHttpRoutes(((AgentHttpHandlerProvider) beanProvider).getAgentHttpHandlers());
        }
        // 当实现了 AgentInitializingBean 接口，执行初始化
        if (beanProvider instanceof AgentInitializingBean) {
            ((AgentInitializingBean) beanProvider).afterPropertiesSet();
        }
        // 当实现了 TracingProvider 接口，设置 tracing 到上下文管理器中，因此只会有一个
        if (beanProvider instanceof TracingProvider) {
            TracingProvider tracingProvider = (TracingProvider) beanProvider;
            contextManager.setTracing(tracingProvider);
        }
        // 当实现了 MetricProvider 接口，设置 metric 到上下文管理器中，因此只会有一个
        if (beanProvider instanceof MetricProvider) {
            contextManager.setMetric((MetricProvider) beanProvider);
        }
    }

    // getAgentBuilder 初始化 bytebuddy 的 AgentBuilder，设置相关策略，以及忽略的类名
    public static AgentBuilder getAgentBuilder(Configs config, boolean test) {
        // config may use to add some classes to be ignored in future
        long buildBegin = System.currentTimeMillis();
        // 构造 bytebuddy 的 AgentBuilder
        AgentBuilder builder = new AgentBuilder.Default()
            // 设置监听器
            .with(LISTENER)
            // 设置重定义策略为 retransformation，即只是对已有的 class bytecode 进行动态修改，而不是基于 .class 文件的重新定义，那是 redefine
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            // 设置初始化策略为 NoOp，即不对任何类执行初始化操作
            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
            // 设置类型策略为 redefine，即基于 .class 文件的重新定义，
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            // 对 ClassLoader 保持强引用，并在无法找到类时回退到 System Class Loader
            .with(AgentBuilder.LocationStrategy.ForClassLoader.STRONG
                .withFallbackTo(ClassFileLocator.ForClassLoader.ofSystemLoader()));
        // 构造无需增强的类匹配器，
        AgentBuilder.Ignored ignore = builder.ignore(isSynthetic()) // 忽略合成的方法
            // 忽略 sun. 开头，但排除 sun.net.www.protocol.http 的类名，
            .or(nameStartsWith("sun.").and(not(nameStartsWith("sun.net.www.protocol.http")) ))
            // 忽略 com.sun. 开头的类名
            .or(nameStartsWith("com.sun."))
            // 忽略 brave. 开头的类名，这是分布式追踪的库
            .or(nameStartsWith("brave."))
            // 忽略 zipkin2. 开头的类名，这是分布式追踪的 Zipkin
            .or(nameStartsWith("zipkin2."))
            // 忽略 com.fasterxml，这是 Jackson 的包，主要用于 JSON 处理
            .or(nameStartsWith("com.fasterxml"))
            // 忽略 org.apache.logging 开头，但排除 org.apache.logging.log4j.spi.AbstractLogger 的子类，
            // 这样就不会增强 log4j2 的 Logger 类，但会增强其他的 logging 类
            .or(nameStartsWith("org.apache.logging")
                .and(not(hasSuperClass(named("org.apache.logging.log4j.spi.AbstractLogger")))))
            // 忽略 kotlin. 开头的类名，这是 Kotlin 的包，主要用于 Kotlin 语言的支持
            .or(nameStartsWith("kotlin."))
            // 忽略 java. 开头的类名，这是 java 标准库的包
            .or(nameStartsWith("javax."))
            // 忽略 net.bytebuddy. 开头的类名，这是 ByteBuddy 的包，主要用于动态字节码生成和修改
            .or(nameStartsWith("net.bytebuddy."))
            // 忽略 com\.sun\.proxy\.\$Proxy.+ 开头的类名，这是 Java 动态代理生成的类，主要用于动态代理的实现
            .or(nameStartsWith("com\\.sun\\.proxy\\.\\$Proxy.+"))
            // 忽略 java\.lang\.invoke\.BoundMethodHandle\$Species_L.+ 开头的类名，这是 Java 语言中 lambda 表达式和方法引用生成的类，主要用于 lambda 表达式和方法引用的实现
            .or(nameStartsWith("java\\.lang\\.invoke\\.BoundMethodHandle\\$Species_L.+"))
            // 忽略 org.junit. 开头的类名，这是 junit 的包，主要用于单元测试的支持
            .or(nameStartsWith("org.junit."))
            // 忽略 junit. 开头的类名，这是 junit 的包，主要用于单元测试的支持
            .or(nameStartsWith("junit."))
            // 忽略 com.intellij. 开头的类名，这是 IntelliJ IDEA 的包，主要用于开发工具的支持
            .or(nameStartsWith("com.intellij."));

        // config used here to avoid warning of unused
        // 如果不是测试环境，且配置不为 null，则忽略 com.megaease.easeagent. 开头的类名，这样就不会增强 agent 自身的类，但会增强其他的类
        if (!test && config != null) {
            builder = ignore
                .or(nameStartsWith("com.megaease.easeagent."));
        } else {
            builder = ignore;
        }
        LOGGER.info("AgentBuilder use time: {}", (System.currentTimeMillis() - buildBegin));
        return builder;
    }

    // registerMBeans 将 ConfigManagerMXBean 注册为 MBean，注册完成后可以通过 JMX 来访问和修改配置
    @SneakyThrows
    static void registerMBeans(ConfigManagerMXBean conf) {
        long begin = System.currentTimeMillis();
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        // 使用名称为：com.megaease.easeagent:type=ConfigManager
        ObjectName mxBeanName = new ObjectName(MX_BEAN_OBJECT_NAME);
        // TODO 多余的代码
//        ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
        // 注册 MXBean
        mbs.registerMBean(conf, mxBeanName);
        LOGGER.info("Register {} as MBean {}, use time: {}",
            conf.getClass().getName(), mxBeanName, (System.currentTimeMillis() - begin));
    }

    private static ElementMatcher<ClassLoader> protectedLoaders() {
        return isBootstrapClassLoader().or(is(Bootstrap.class.getClassLoader()));
    }

    // wrapConfig 将全局配置和类加载器封装到 WrappedConfigManager 中，并注册到 MBean 中，完成配置的暴露和管理
    private static void wrapConfig(GlobalConfigs configs) {
        // 将类加载器和config封装到 WrappedConfigManager 中
        WrappedConfigManager wrappedConfigManager = new WrappedConfigManager(Bootstrap.class.getClassLoader(), configs);
        // 将其注册到 MBean 中，使用 com.megaease.easeagent:type=ConfigManager 可以访问
        registerMBeans(wrappedConfigManager);
        // 保存到全局 Agent
        GlobalAgentHolder.setWrappedConfigManager(wrappedConfigManager);
    }

    // bytebuddy 的 AgentBuilder.Listener 实现类，用于监听类的加载和转换过程中所发生事件通知的监听器
    // 此处仅在发生 Transformation 和 Error 时输出日志，其他事件忽略
    private static final AgentBuilder.Listener LISTENER = new AgentBuilder.Listener() {
        @Override
        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
            // ignored
        }

        @Override
        public void onTransformation(TypeDescription td, ClassLoader ld, JavaModule m, boolean loaded, DynamicType dt) {
            LOGGER.debug("onTransformation: {} loaded: {} from classLoader {}", td, loaded, ld);
        }

        @Override
        public void onIgnored(TypeDescription td, ClassLoader ld, JavaModule m, boolean loaded) {
            // ignored
        }

        @Override
        public void onError(String name, ClassLoader ld, JavaModule m, boolean loaded, Throwable error) {
            LOGGER.debug("Just for Debug-log, transform ends exceptionally, " +
                    "which is sometimes normal and sometimes there is an error: {} error:{} loaded: {} from classLoader {}",
                name, error, loaded, ld);
        }

        @Override
        public void onComplete(String name, ClassLoader ld, JavaModule m, boolean loaded) {
            // ignored
        }
    };
}
