# Modules

## build

主要用于构建 easeagent.jar。核心是 `src/assembly/src.xml`，用于配置构建过程中的文件和目录结构。
其次是 `src/main/resources` 配置，决定了 easeagent 的运行行为。
最后是 `src/main/java` 这是用于调用 `core` 进行初始化的类。

## loader

这是 easeagent 的启动入口，用于读取 easeagent.jar 的路径，并初始化 `EaseAgentClassLoader`，用于隔离 agent 和应用的类加载器。
以及将需要共享的类加载到 `BootstrapClassLoader` 中。
