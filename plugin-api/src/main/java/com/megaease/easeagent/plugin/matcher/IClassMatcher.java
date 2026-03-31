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

package com.megaease.easeagent.plugin.matcher;

import com.megaease.easeagent.plugin.matcher.operator.AndClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.NegateClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.Operator;
import com.megaease.easeagent.plugin.matcher.operator.OrClassMatcher;

/**
 * 顶层的类拦截器的接口，byte buddy 层面对应 Junction<TypeDescription>
 * 提供了 and、or、negate 等操作符，方便组合多个 ClassMatcher 进行复杂的匹配
 * 方法 -> ease agent -> byte buddy
 * and -> AndClassMatcher -> Junction#and
 * or -> OrClassMatcher -> Junction#or
 * negate -> NegatingClassMatcher -> NegatingMatcher
 */
public interface IClassMatcher extends Operator<IClassMatcher>, Matcher {
    default IClassMatcher and(IClassMatcher m) {
        return new AndClassMatcher(this, m);
    }

    default IClassMatcher or(IClassMatcher m) {
        return new OrClassMatcher(this, m);
    }

    default IClassMatcher negate() {
        return new NegateClassMatcher(this);
    }
}
