package com.demo.logger;

import lombok.Setter;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.jdbc.BaseJdbcLogger;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 记录SQL字段信息的logger
 * invoke方法参考 PreparedStatementLogger
 */
public class SqlParamLogger extends BaseJdbcLogger implements InvocationHandler {

    static final Log NO_LOG = new NoLoggingImpl("");

    InvocationHandler handler;

    @Setter
    SqlLogger sqlLogger;

    public SqlParamLogger(InvocationHandler handler) {
        super(NO_LOG, 0);
        this.handler = handler;
    }

    public String getParameterValueString() {
        return super.getParameterValueString();
    }

    public String getSqlString() {
        if (sqlLogger == null) {
            return null;
        }
        return sqlLogger.getSqlString();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        if (SET_METHODS.contains(method.getName())) {
            if ("setNull".equals(method.getName())) {
                setColumn(params[0], null);
            } else {
                setColumn(params[0], params[1]);
            }
        }
        return handler.invoke(proxy, method, params);
    }
}
