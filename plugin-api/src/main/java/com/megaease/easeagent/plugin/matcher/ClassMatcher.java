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
import com.megaease.easeagent.plugin.asm.Modifier;
import com.megaease.easeagent.plugin.enums.ClassMatch;
import com.megaease.easeagent.plugin.matcher.operator.AndClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.NotClassMatcher;
import com.megaease.easeagent.plugin.matcher.operator.Operator;
import com.megaease.easeagent.plugin.matcher.operator.OrClassMatcher;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@SuppressWarnings("unused")
public class ClassMatcher implements Operator<ClassMatcher>, Matcher {
    private String name;
    private ClassMatch matchType;
    private int modifier = Modifier.ACC_NONE;
    private String classLoader;

    protected ClassMatcher() {
    }

    public ClassMatcher isPublic() {
        this.modifier |= Modifier.ACC_PUBLIC;
        return this;
    }

    public ClassMatcher isPrivate() {
        this.modifier |= Modifier.ACC_PRIVATE;
        return this;
    }

    public ClassMatcher isAbstract() {
        this.modifier |= Modifier.ACC_ABSTRACT;
        return this;
    }

    public ClassMatcher classLoader(String classLoaderName) {
        this.classLoader = classLoaderName;
        return this;
    }


    @Override
    public ClassMatcher and(ClassMatcher matcher) {
        return new AndClassMatcher(this, matcher);
    }

    @Override
    public ClassMatcher or(ClassMatcher matcher) {
        return new OrClassMatcher(this, matcher);
    }

    @Override
    public ClassMatcher not() {
        return new NotClassMatcher(this);
    }
}

