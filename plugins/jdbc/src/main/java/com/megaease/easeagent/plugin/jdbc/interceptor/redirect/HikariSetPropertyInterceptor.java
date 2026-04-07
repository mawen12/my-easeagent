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

package com.megaease.easeagent.plugin.jdbc.interceptor.redirect;

import com.megaease.easeagent.plugin.interceptor.Interceptor;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.annotation.AdviceTo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.api.middleware.Redirect;
import com.megaease.easeagent.plugin.api.middleware.RedirectProcessor;
import com.megaease.easeagent.plugin.api.middleware.ResourceConfig;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.jdbc.JdbcRedirectPlugin;
import com.megaease.easeagent.plugin.jdbc.advice.HikariDataSourceAdvice;
import com.megaease.easeagent.plugin.utils.common.StringUtils;

/**
 * 拦截 com.zaxxer.hikari.HikariConfig 的 setJdbcUrl、setUsername 和 setPassword 方法，
 * 在方法执行之前执行，重定向数据库连接的 URL、用户名和密码。
 *
 * 一般是启动时，直接将 HikariConfig 的 jdbcUrl、username 和 password 重定向到配置的值，实现数据库连接的重定向。
 */
@AdviceTo(value = HikariDataSourceAdvice.class, plugin = JdbcRedirectPlugin.class)
public class HikariSetPropertyInterceptor implements Interceptor {
    private static final Logger LOGGER = EaseAgent.getLogger(HikariSetPropertyInterceptor.class);

    @Override
    public void before(MethodInfo methodInfo, Context context) {
        // 读取数据库配置
        ResourceConfig cnf = Redirect.DATABASE.getConfig();
        if (cnf == null) {
            // 没有配置则不执行
            return;
        }
        if (methodInfo.getMethod().equals("setJdbcUrl")) {// 处理 setJdbcUrl 方法
            String jdbcUrl = cnf.getFirstUri();
            LOGGER.info("Redirect JDBC Url: {} to {}", methodInfo.getArgs()[0], jdbcUrl);
            // 重定向到配置的 jdbcUrl
            // 修改方法参数为新的 jdbcUrl，实现 jdbcUrl 的重定向
            methodInfo.changeArg(0, jdbcUrl);
            RedirectProcessor.redirected(Redirect.DATABASE, jdbcUrl);
        } else if (methodInfo.getMethod().equals("setUsername") && StringUtils.isNotEmpty(cnf.getUserName())) {
            LOGGER.info("Redirect JDBC Username: {} to {}", methodInfo.getArgs()[0], cnf.getUserName());
            // 修改方法参数为新的 username，实现 username 的重定向
            methodInfo.changeArg(0, cnf.getUserName());
        } else if (methodInfo.getMethod().equals("setPassword") && StringUtils.isNotEmpty(cnf.getPassword())) {
            LOGGER.info("Redirect JDBC Password: *** to ***");
            // 修改方法参数为新的 password，实现 password 的重定向
            methodInfo.changeArg(0, cnf.getPassword());
        }
    }

    @Override
    public String getType() {
        return Order.REDIRECT.getName();
    }

    @Override
    public int order() {
        return Order.REDIRECT.getOrder();
    }
}
