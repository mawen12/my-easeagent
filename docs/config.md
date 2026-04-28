# Config

代表类配置，提供配置读取、配置动态刷新的能力。

## 初始化

通过 `ConfigFactory#loadConfigs` 加载配置：
- 读取默认配置 easeagent.jar/agent.properties
- 读取用户自定义的配置文件
- 读取环境变量的配置
- 合并上述配置

## 分类

- Configs 保存
- GlobalConfigs 支持配置的更新
- PluginConfig 特定插件的配合
