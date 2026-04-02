# Flow

## 启动流程

1. [loader]Main#premain
   1. 找到 easeagent.jar 的位置
   2. 处理嵌套 jar 的情况，并使用自定义的 ClassLoader 加载部分 jar
   3. 将 boot/ 目录下的 jar 添加到 Bootstrap class loader
   4. 特殊处理 log4j2/ 目录，并为其实现特定的 ClassLoader
2. [build]StartBootstrap#premain
3. [core]Bootstrap#start
   1. 处理com.megaease.easeagent.plugin.AppendBootstrapLoader
   2. 处理配置
   3. 初始化 ContextManager
   4. 初始化 http server
   5. 初始化 agent report
   6. 初始化 agent build
   7. **加载 Plugin**
   8. 加载 BeanProvider
   9. 安装

