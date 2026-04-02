package com.megaease.easeagent.plugin.field;

/**
 * 当 ClassTransformation 指定了 typeFieldAccessor，此时会通过 byte buddy 进行增强，
 * 来为其实现该接口
 */
public interface TypeFieldGetter {
    <T> T getEaseAgent$$TypeField$$Data();

    static <T> T get(Object o) {
        if (o instanceof TypeFieldGetter) {
            return ((TypeFieldGetter) o).getEaseAgent$$TypeField$$Data();
        }
        return null;
    }
}
