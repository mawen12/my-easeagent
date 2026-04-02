package com.megaease.easeagent.core.plugin.transformer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.field.TypeFieldGetter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.utility.JavaModule;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 读取类中私有字段的 Transformer。通过对类实现接口 TypeFieldGetter，然后添加一个 FieldAccessor 来实现
 */
public class TypeFieldTransformer implements AgentBuilder.Transformer {
    private static final Logger log = LoggerFactory.getLogger(TypeFieldTransformer.class);

    private static final ConcurrentHashMap<String, Cache<ClassLoader, Boolean>> FIELD_MAP = new ConcurrentHashMap<>();
    // 要读取的字段名
    private final String fieldName;
    private final Class<?> accessor;
    private final AgentBuilder.Transformer.ForAdvice transformer;
    public TypeFieldTransformer(String fieldName) {
        this.fieldName = fieldName;
        this.accessor = TypeFieldGetter.class;
        this.transformer = new AgentBuilder.Transformer
            .ForAdvice(Advice.withCustomMapping())
            // 指定查找该类时，使用该类的 Class Loader，确保能找到该类
            .include(getClass().getClassLoader());
    }

    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b,
                                            TypeDescription td, ClassLoader cl, JavaModule m) {
        if (check(td, this.accessor, cl) && this.fieldName != null) {
            try {
                // 实现 TypeFieldGetter 接口
                b = b.implement(this.accessor)
                    // 添加一个对 fieldName 字段的访问器
                    .intercept(FieldAccessor.ofField(this.fieldName));
            } catch (Exception e) {
                log.debug("Type:{} add extend field again!", td.getName());
            }
            // 进行增强
            return transformer.transform(b, td, cl, m);
        }
        return b;
    }

    /**
     * Avoiding add a accessor interface to a class repeatedly
     *
     * @param td       represent the class to be enhanced
     * @param accessor access interface class
     * @param cl       current classloader
     * @return return true when it is the first time
     */
    private static boolean check(TypeDescription td, Class<?> accessor, ClassLoader cl) {
        String key = td.getCanonicalName() + accessor.getCanonicalName();

        Cache<ClassLoader, Boolean> checkCache = FIELD_MAP.get(key);
        if (checkCache == null) {
            Cache<ClassLoader, Boolean> cache = CacheBuilder.newBuilder().weakKeys().build();
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            cache.put(cl, true);
            checkCache = FIELD_MAP.putIfAbsent(key, cache);
            if (checkCache == null) {
                return true;
            }
        }

        return checkCache.getIfPresent(cl) == null;
    }
}
