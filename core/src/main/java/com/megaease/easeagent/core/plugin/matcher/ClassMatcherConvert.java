/*
 * Copyright (c) 2021, MegaEase
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

package com.megaease.easeagent.core.plugin.matcher;

import com.megaease.easeagent.plugin.asm.Modifier;
import com.megaease.easeagent.plugin.matcher.ClassMatcher;
import com.megaease.easeagent.plugin.matcher.IClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.AndClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.NegateClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.OrClassMatcher;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.matcher.NegatingMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * ClassMatcher 转换器，从 ease agent 的 IClassMatcher 转换为 ByteBuddy 的 Junction<TypeDescription>
 */
public class ClassMatcherConvert
    implements Converter<IClassMatcher, Junction<TypeDescription>> {
    public static final ClassMatcherConvert INSTANCE = new ClassMatcherConvert();

    // convert 从 ease agent 的 IClassMatcher 转换为 byte buddy 的 Junction<TypeDescription>
    @Override
    public Junction<TypeDescription> convert(IClassMatcher source) {
        if (source == null) {
            return null;
        }

        // 处理 And
        if (source instanceof AndClassMatcher) {
            AndClassMatcher andMatcher = (AndClassMatcher) source;
            Junction<TypeDescription> leftMatcher = this.convert(andMatcher.getLeft());
            Junction<TypeDescription> rightMatcher = this.convert(andMatcher.getRight());
            // 将 and 的 left 和 right 转换为 Junction#And
            return leftMatcher.and(rightMatcher);
        // 处理 Or
        } else if (source instanceof OrClassMatcher) {
            OrClassMatcher andMatcher = (OrClassMatcher) source;
            Junction<TypeDescription> leftMatcher = this.convert(andMatcher.getLeft());
            Junction<TypeDescription> rightMatcher = this.convert(andMatcher.getRight());
            // 将 or 的 left 和 right 转换为 Junction#Or
            return leftMatcher.or(rightMatcher);
        // 处理 Negate
        } else if (source instanceof NegateClassMatcher) {
            NegateClassMatcher matcher = (NegateClassMatcher) source;
            Junction<TypeDescription> notMatcher = this.convert(matcher.getMatcher());
            // 将 negate 的 matcher 转换为 NegatingMatcher
            return new NegatingMatcher<>(notMatcher);
        }

        // 忽略非 ClassMatcher,
        if (!(source instanceof ClassMatcher)) {
            return null;
        }

        // 处理 ClassMatcher
        return this.convert((ClassMatcher) source);
    }

    private Junction<TypeDescription> convert(ClassMatcher matcher) {
        Junction<TypeDescription> c;
        switch (matcher.getMatchType()) {
            // NAMED -> ElementMatchers#named
            case NAMED:
                c = named(matcher.getName());
                break;
            // SUPER_CLASS, INTERFACE  -> ElementMatchers#hasSuperType
            case SUPER_CLASS:
            case INTERFACE:
                c = hasSuperType(named(matcher.getName()));
                break;
            // ANNOTATION -> ElementMatchers#isAnnotatedWith
            case ANNOTATION:
                c = isAnnotatedWith(named(matcher.getName()));
                break;
            default:
                return null;
        }

        // 处理 modifier
        Junction<TypeDescription> mc = fromModifier(matcher.getModifier(), false);
        if (mc != null) {
            c = c.and(mc);
        }
        // 处理 not modifier
        mc = fromModifier(matcher.getNotModifier(), true);
        if (mc != null) {
            c = c.and(mc);
        }

        // TODO: classloader matcher

        return c;
    }

    // fromModifier
    Junction<TypeDescription> fromModifier(int modifier, boolean not) {
        Junction<TypeDescription> mc = null;
        // 表示设置了 modifier，如果没有设置的话，不进行处理
        // modifier = 0,那么对其 & 也为0
        if ((modifier & ClassMatcher.MODIFIER_MASK) != 0) {
            // ACC_ABSTRACT -> ElementMatchers#isAbstract
            if ((modifier & Modifier.ACC_ABSTRACT) != 0) {
                mc = isAbstract();
            }
            // ACC_PUBLIC -> ElementMatchers#isPublic
            if ((modifier & Modifier.ACC_PUBLIC) != 0) {
                if (mc != null) {
                    mc = not ? mc.or(isPublic()) : mc.and(isPublic());
                } else {
                    mc = isPublic();
                }
            }
            // ACC_PRIVATE -> ElementMatchers#isPrivate
            if ((modifier & Modifier.ACC_PRIVATE) != 0) {
                if (mc != null) {
                    mc = not ? mc.or(isPrivate()) : mc.and(isPrivate());
                } else {
                    mc = isPrivate();
                }
            }

            // ACC_INTERFACE -> ElementMatchers#isInterface
            if ((modifier & Modifier.ACC_INTERFACE) != 0) {
                if (mc != null) {
                    mc = not ? mc.or(isInterface()) : mc.and(isInterface());
                } else {
                    mc = isInterface();
                }
            }

            // ACC_PROTECTED -> ElementMatchers#isProtected
            if ((modifier & Modifier.ACC_PROTECTED) != 0) {
                if (mc != null) {
                    mc = not ? mc.or(isProtected()) : mc.and(isProtected());
                } else {
                    mc = isProtected();
                }
            }

            // Not -> NegatingMatcher
            if (not) {
                mc = new NegatingMatcher<>(mc);
            }
        }
        return mc;
    }
}
