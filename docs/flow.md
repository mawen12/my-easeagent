# Flow

## 启动流程

1. Main#premain
   1.1. 找到 easeagent.jar 的位置
   1.2. 处理嵌套 jar 的情况，并使用自定义的 ClassLoader 加载部分 jar
   1.3. 将 boot/ 目录下的 jar 添加到 Bootstrap class loader
   1.4. 特殊处理 log4j2/ 目录，并为其实现特定的 ClassLoader
2. StartBootstrap#premain
3. Bootstrap#start
   3.1.  

## 
