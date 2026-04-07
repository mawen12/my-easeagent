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
import com.megaease.easeagent.plugin.jdbc.advice.JdbcStatementAdvice;
import com.megaease.easeagent.plugin.jdbc.common.SqlInfo;

import java.sql.Statement;

/**
 * 拦截 java.sql.Statement 的 execute*、addBatch 和 clearBatch 方法，
 * 在方法执行前获取 SQL 语句信息，并将其存储在上下文中，以便后续的拦截器：JdbcStmMetricInterceptor 可以获取到 SQL 语句信息进行统计和监控。
 */
@AdviceTo(value = JdbcStatementAdvice.class, plugin = JdbcTracingPlugin.class)
@AdviceTo(value = JdbcStatementAdvice.class, qualifier = "batch", plugin = JdbcTracingPlugin.class)
public class JdbcStmPrepareSqlInterceptor implements NonReentrantInterceptor {
    private static final Logger log = EaseAgent.getLogger(JdbcStmPrepareSqlInterceptor.class);

    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
        // 获取方法调用的 Statement 对象
        Statement stm = (Statement) methodInfo.getInvoker();
        if (!(stm instanceof DynamicFieldAccessor)) {
            log.warn("statement must implements " + DynamicFieldAccessor.class.getName());
            // 未实现 DynamicFieldAccessor 接口，无法存储 SQL 信息，直接返回
            return;
        }

        // 通过 DynamicFieldAccessor 获取 Statement 对象的动态字段值，即 SqlInfo 对象
        SqlInfo sqlInfo = AgentDynamicFieldAccessor.getDynamicFieldValue(stm);
        if (sqlInfo == null) {
            /*
             * This happens:
             * 1. StatementA contains StatementB.
             * 2. Intercept: statementA = con.preparedStatement.
             * 3. Not intercept: statementB = new StatementB(). StatementB can not be set dynamicField value.
             * 4. StatementB invoke other method, like: clearBatch, so interceptor will find dynamicField value is null.
             */
            return;
        }
        String sql = null;
        if (methodInfo.getArgs() != null && methodInfo.getArgs().length > 0) {
            // 如果拦截的方法有参数，则尝试将第一个参数作为 SQL 语句进行处理
            sql = (String) methodInfo.getArgs()[0];
        }
        String method = methodInfo.getMethod();
        if (method.equals("addBatch")) {
            /*
             * user creates PreparedStatement with con.preparedStatement(sql).
             * User can invokes PreparedStatement.addBatch() multi times.
             * In this scenario, sqlInfo should has only one sql.
             */
            // 处理 addBatch 方法，如果 SQL 语句不为 null，则将其添加到 SqlInfo 中，并标记为批处理 SQL
            if (sql != null) {
                sqlInfo.addSql(sql, true);
            }
        } else if (method.equals("clearBatch")) {
            // 处理 clearBatch 方法，清除 SqlInfo 中的 SQL 语句列表
            sqlInfo.clearSql();
        } else if (method.startsWith("execute") && sql != null) {
            // 处理 execute 方法，如果 SQL 语句不为 null，则将其添加到 SqlInfo 中，并标记为非批处理 SQL
            sqlInfo.addSql(sql, false);
        }
        // 将更新后的 SqlInfo 对象保存到上下文中，以便后续的拦截器：JdbcStmMetricInterceptor 可以获取到 SQL 语句信息进行统计和监控
        context.put(SqlInfo.class, sqlInfo);
    }

    @Override
    public String getType() {
        return Order.TRACING.getName();
    }

    @Override
    public int order() {
        return Order.HIGH.getOrder();
    }
}
