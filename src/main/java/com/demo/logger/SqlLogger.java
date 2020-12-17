package com.demo.logger;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.jdbc.BaseJdbcLogger;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 记录SQL语句信息的logger
 * invoke方法参考 ConnectionLogger
 */
public class SqlLogger extends BaseJdbcLogger implements InvocationHandler {

    static final Log NO_LOG = new NoLoggingImpl("");

    InvocationHandler handler;

    String sql;

    public SqlLogger(InvocationHandler handler) {
        super(NO_LOG, 0);
        this.handler = handler;
    }

    public String getSqlString() {
        return sql == null ? sql : removeExtraWhitespace(sql);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        if ("prepareStatement".equals(method.getName()) || "prepareCall".equals(method.getName())) {
            this.sql = (String) params[0];
        }
        return this.handler.invoke(proxy, method, params);
    }
}
