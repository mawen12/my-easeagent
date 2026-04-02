# Byte Buddy

此项目使用 `byte buddy` 来实现 `Java agent`。

## AgentBuilder

## Transformer

通过实现 `AgentBuilder.Transformer` 实现对方法的增强。
最核心的使用 `AgentForAdvice`

### AgentForAdvice

copy 了 `byte buddy` 的 `ForAdvice`，仅用于替换将 `Advice` 替换为 `AgentAdvice`

### AgentAdvice

copy 了 `byte buddy` 的 `Advice`，仅用于将 `AdviceRegistry` 应用。

### ForAdviceTransformer

从 `Points#methodMatcher`(原始定义) -> `[]MethodTransformation`(中间态，其中 MethodMatch -> ElementMatcher) -> `[]ForAdviceTransformer`(byte buddy)。



