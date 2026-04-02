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
import com.megaease.easeagent.plugin.matcher.IMethodMatcher;
import com.megaease.easeagent.plugin.matcher.MethodMatcher;
import com.megaease.easeagent.plugin.matcher.operator.AndMethodMatcher;
import com.megaease.easeagent.plugin.matcher.operator.NegateMethodMatcher;
import com.megaease.easeagent.plugin.matcher.operator.OrMethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.matcher.NegatingMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * MethodMatcher 转换器，从 ease agent 的 IMethodMatcher 转换为 ByteBuddy 的 Junction<MethodDescription>
 */
public class MethodMatcherConvert
    implements Converter<IMethodMatcher, Junction<MethodDescription>> {

    public static final MethodMatcherConvert INSTANCE = new MethodMatcherConvert();

    // convert 从 ease agent 的 IMethodMatcher 转换为 byte buddy 的 Junction<MethodDescription>
    @Override
    public Junction<MethodDescription> convert(IMethodMatcher source) {
        if (source == null) {
            return null;
        }

        // 处理 And
        if (source instanceof AndMethodMatcher) {
            AndMethodMatcher andMatcher = (AndMethodMatcher) source;
            Junction<MethodDescription> leftMatcher = this.convert(andMatcher.getLeft());
            Junction<MethodDescription> rightMatcher = this.convert(andMatcher.getRight());
            // 将 and 的 left 和 right 转换为 Junction#And
            return leftMatcher.and(rightMatcher);
        // 处理 Or
        } else if (source instanceof OrMethodMatcher) {
            OrMethodMatcher andMatcher = (OrMethodMatcher) source;
            Junction<MethodDescription> leftMatcher = this.convert(andMatcher.getLeft());
            Junction<MethodDescription> rightMatcher = this.convert(andMatcher.getRight());
            // 将 or 的 left 和 right 转换为 Junction#Or
            return leftMatcher.or(rightMatcher);
        // 处理 Negate
        } else if (source instanceof NegateMethodMatcher) {
            NegateMethodMatcher matcher = (NegateMethodMatcher) source;
            Junction<MethodDescription> notMatcher = this.convert(matcher.getMatcher());
            // 将 negate 的 matcher 转换为 NegatingMatcher
            return new NegatingMatcher<>(notMatcher);
        }

        // 忽略非 MethodMatcher
        if (!(source instanceof MethodMatcher)) {
            return null;
        }

        // 处理 MethodMatcher
        return this.convert((MethodMatcher) source);
    }

    private Junction<MethodDescription> convert(MethodMatcher matcher) {
        Junction<MethodDescription> c = null;
        if (matcher.getName() != null && matcher.getNameMatchType() != null) {
            switch (matcher.getNameMatchType()) {
                // EQUALS -> ElementMatchers#named / ElementMatchers#isConstructor
                case EQUALS:
                    if ("<init>".equals(matcher.getName())) {
                        c = isConstructor();
                    } else {
                        c = named(matcher.getName());
                    }
                    break;
                // START_WITH -> ElementMatchers#nameStartsWith
                case START_WITH:
                    c = nameStartsWith(matcher.getName());
                    break;
                // END_WITH  -> ElementMatchers#nameEndsWith
                case END_WITH:
                    c = nameEndsWith(matcher.getName());
                    break;
                // CONTAINS  -> ElementMatchers#nameContains
                case CONTAINS:
                    c = nameContains(matcher.getName());
                    break;
                default:
                    return null;
            }
        }

        // 处理 modifier
        Junction<MethodDescription> mc = fromModifier(matcher.getModifier(), false);
        if (mc != null) {
            c = c == null ? mc : c.and(mc);
        }
        // 处理 notModifier
        mc = fromModifier(matcher.getNotModifier(), true);
        if (mc != null) {
            c = c == null ? mc : c.and(mc);
        }
        // 处理 returnType
        if (matcher.getReturnType() != null) {
            // returns -> ElementMatchers#returns(ElementMatchers#named)
            mc = returns(named(matcher.getReturnType()));
            c = c == null ? mc : c.and(mc);
        }
        // 处理 argsLength
        if (matcher.getArgsLength() > -1) {
            // argsLength -> ElementMatchers#takesArguments
            mc = takesArguments(matcher.getArgsLength());
            c = c == null ? mc : c.and(mc);
        }
        // 处理 args
        String[] args = matcher.getArgs();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    // args -> ElementMatchers#takesArgument
                    mc = takesArgument(i, named(args[i]));
                    c = c == null ? mc : c.and(mc);
                }
            }
        }

        // TODO 重复代码，和上面的 > -1 的处理方式一样，是否需要合并
        if (matcher.getArgsLength() >= 0) {
            mc = takesArguments(matcher.getArgsLength());
            c = c == null ? mc : c.and(mc);
        }

        // 处理所在类
        if (matcher.getOverriddenFrom() != null) {
            Junction<TypeDescription> cls = ClassMatcherConvert.INSTANCE.convert(matcher.getOverriddenFrom());
            // overriddenFrom -> ElementMatchers#isOverriddenFrom
            mc = isOverriddenFrom(cls);
            c = c == null ? mc : c.and(mc);
        }

        return c;
    }

    Junction<MethodDescription> fromModifier(int modifier, boolean not) {
        Junction<MethodDescription> mc = null;
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
            // ACC_PROTECTED -> ElementMatchers#isProtected
            if ((modifier & Modifier.ACC_PROTECTED) != 0) {
                if (mc != null) {
                    mc = not ? mc.or(isProtected()) : mc.and(isProtected());
                } else {
                    mc = isProtected();
                }
            }
            // ACC_STATIC -> ElementMatchers#isStatic
            if ((modifier & Modifier.ACC_STATIC) != 0) {
                if (mc != null) {
                    mc = not ? mc.or(isStatic()) : mc.and(isStatic());
                } else {
                    mc = isStatic();
                }
            }
            if (not) {
                mc = new NegatingMatcher<>(mc);
            }
        }
        return mc;
    }
}
