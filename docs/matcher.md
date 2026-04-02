# Matcher

匹配器用于匹配 `ClassLoader`， `Class`, `Method`。
该设计对应了 `byte buddy` 中的 `ElementMatcher`。 

## ClassLoaderMatcher

用于匹配 `ClassLoader`，支持一个或多个匹配，支持 `all`, `bootstrap`, `external`, `system`, `agent`, `custom name`。
其底层对应 `byte buddy` 的 `ElementMatcher<ClassLoader>`。

通过 `ClassLoaderMatcherConvert` 实现向 `byte buddy` 的领域转换。

## ClassMatcher

用于匹配 `Class`，支持一个或多个匹配，支持 `class name`，`modifier`，`hierachy`。
其底层对应 `byte buddy` 的 `ElementMatcher.Junction<TypeDescription>`。

通过 `ClassMatcherConvert` 实现向 `byte buddy` 的领域转换。

## MethodMatcher

用于匹配 `Method`，支持一个或多个匹配，支持 `method name`, `return type`, `args`, `args length`, `modifier`, `overridden from`, `qualifier`。
其底层对应 `byte buddy` 的 `ElementMatcher.Junction<MethodDescription>`。

通过 `MethodMatcherConvert` 实现向 `byte buddy` 的领域转换。

# 应用

`Matcher` 被 `Points` 接口及其实现所使用。`Points` 职责是定位要被增强的的类的方法。
当定义好 `Points` 之后，在启动阶段的 `Bootstrap#start` -> `PluginLoader#load` -> `PluginLoader#pointsLoader` -> `PluginRegistry#register` 进行注册。
再在 `PluginLoader#Load` -> `PluginLoader#classTransformationLoad` -> `PluginRegistry#registerClassTransformation` 中读取 `Points`,
使用 `MatcherConvert` 转换为 `ClassTransformation`。
