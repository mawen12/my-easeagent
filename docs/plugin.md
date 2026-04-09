# Plugin

## Plugin

定义了插件的作用范围以及顺序。
通过 `domain`，`namespace` 来界定范围，然后使用 `order` 定义执行顺序。

## Points

定义了插件的作用点。
通过 `ClassLoader`，`Class`，`Method` 来匹配作用点。
并且可以定义作用点的行为，比如 `addDynamicField`,`TypeFieldAccess`。
具体的匹配和对字段的增强，由 byte buddy 负责。

## Interceptor 

定义了拦截时触发的行为。
通过 `before`，`after` 来实现在目标方法执行前后触发的行为。


## 实现

每一个 Points 对应一个或多个Class和一个或多个Method。
每一个 Points 可以被多个 Interceptor 所绑定，这是因为每个 Interceptor 都有其职责，比如 Metric 和 Tracing。
每个 Points 都可以都会生成一个 ClassTransformation，
而每个 MethodMatcher 都会生成一个 MethodTransformation，
每个 MethodMatcher 都会有至少一个 byte buddy transformer：
    1. 必然存在的，就是封装 CommonInlineAdvice，添加 ForAdviceTransformer。
    2. 当 Points 设置了 isAddDynamicField() = true，则添加 DynamicFieldTransformer。
    3. 当 Points 设置了 getTypeFieldAccessor != ""，则添加 TypeFieldTransformer。
上述一个或多个 transformer 被组合时，将使用 CompoundPluginTransformer 来组合它们。
    
CommonInlineAdvice 中会使用 Interceptor， 而该 Interceptor 会通过 AdviceRegistry 动态合并到 Dispatcher#chains 中。
而 AdviceRegistry 是已经明确了方法的的注入，由于 Advice 会被多个 Interceptor 使用。因此此时将 Interceptor 进行合并管理。
