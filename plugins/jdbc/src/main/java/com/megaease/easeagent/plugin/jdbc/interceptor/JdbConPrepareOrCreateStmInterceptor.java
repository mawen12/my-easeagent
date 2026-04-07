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

package com.megaease.easeagent.plugin.jdbc.interceptor;

import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.annotation.AdviceTo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.field.AgentDynamicFieldAccessor;
import com.megaease.easeagent.plugin.field.DynamicFieldAccessor;
import com.megaease.easeagent.plugin.interceptor.NonReentrantInterceptor;
import com.megaease.easeagent.plugin.jdbc.JdbcTracingPlugin;
import com.megaease.easeagent.plugin.jdbc.advice.JdbcConnectionAdvice;
import com.megaease.easeagent.plugin.jdbc.common.SqlInfo;

import java.sql.Connection;
import java.sql.Statement;

/**
 * 拦截 java.sql.Connection 的 createStatement、prepareCall 和 prepareStatement 方法，
 * 在方法执行后获取返回的 Statement 对象，并将 SQL 语句信息存储在 Statement 对象的动态字段中，
 * 以便后续的 Statement 执行方法能够获取到 SQL 语句信息进行追踪和监控。
 */
@AdviceTo(value = JdbcConnectionAdvice.class, plugin = JdbcTracingPlugin.class)
public class JdbConPrepareOrCreateStmInterceptor implements NonReentrantInterceptor {
    private static final Logger logger = EaseAgent.getLogger(JdbConPrepareOrCreateStmInterceptor.class);

    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
    }

    @Override
    public void doAfter(MethodInfo methodInfo, Context context) {
        // 获取方法返回的语句对象
        Statement stm = (Statement) methodInfo.getRetValue();
        // 创建 SqlInfo 对象，并将 SQL 语句添加到其中
        SqlInfo sqlInfo = new SqlInfo((Connection) methodInfo.getInvoker());
        if (methodInfo.getMethod().startsWith("prepare")
            && methodInfo.getArgs() != null && methodInfo.getArgs().length > 0) {
            // 读取 prepareStatement 或 prepareCall 方法的第一个参数作为 SQL 语句
            String sql = (String) methodInfo.getArgs()[0];
            if (sql != null) {
                sqlInfo.addSql(sql, false);
            }
        }
        if (stm instanceof DynamicFieldAccessor) {
            // 如果实现了 DynamicFieldAccessor 接口，则将 SqlInfo 对象设置为动态字段值
            // 此处会实现，因为 JdbcStatementAdvice 设置了动态字段
            AgentDynamicFieldAccessor.setDynamicFieldValue(stm, sqlInfo);
        } else {
            logger.warn("statement must implements " + DynamicFieldAccessor.class.getName());
        }
    }

    @Override
    public String getType() {
        return Order.TRACING.getName();
    }

    @Override
    public int order() {
        return Order.HIGHEST.getOrder();
    }
}
