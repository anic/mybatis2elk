package com.demo.interceptor;

import com.demo.logger.SqlLogger;
import com.demo.logger.SqlParamLogger;
import com.demo.serivce.ISqlLogService;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * 记录慢SQL日志，替代原有的SqlLogInterceptor
 * 日志记录规则由LogService负责
 */
@Intercepts(value = {
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SqlLogInterceptor implements Interceptor {

    @Autowired
    private ISqlLogService logService;

    /**
     * 拦截目标对象
     *
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String methodName = invocation.getMethod().getName();
        //在prepare方法中，将结果Statement包裹为SqlLogger的代理
        if (StatementHandler.class.isAssignableFrom(invocation.getTarget().getClass())
                && "prepare".equals(methodName)) {
            Connection connection = (Connection) invocation.getArgs()[0];
            SqlLogger sqlLogger = new SqlLogger(Proxy.getInvocationHandler(connection));
            Connection connectionProxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class[]{Connection.class}, sqlLogger);

            try {
                Object o = invocation.getMethod().invoke(
                        invocation.getTarget(),
                        new Object[]{connectionProxy, invocation.getArgs()[1]}
                );

                if (o instanceof PreparedStatement) {
                    SqlParamLogger sqlParamLogger = new SqlParamLogger(Proxy.getInvocationHandler(o));
                    sqlParamLogger.setSqlLogger(sqlLogger);

                    PreparedStatement statementProxy = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class[]{
                            PreparedStatement.class
                    }, sqlParamLogger);
                    return statementProxy;
                }
                return o;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        //在query/update/batch等方法中
        long start = System.currentTimeMillis();
        String exception = null;
        Object result = null;
        try {
            result = proceed(invocation);
            return result;
        } catch (Throwable e) {
            exception = e.getMessage();
            throw e;
        } finally {
            try {
                long costTime = System.currentTimeMillis() - start;
                Object[] args = invocation.getArgs();
                Object handler = Proxy.getInvocationHandler(args[0]);
                String sql = null;
                String param = null;
                if (handler instanceof SqlParamLogger) {
                    SqlParamLogger sqlParamLogger = (SqlParamLogger) handler;
                    param = sqlParamLogger.getParameterValueString();
                    sql = sqlParamLogger.getSqlString();
                }
                Integer resultCount = null;
                if ("update".equals(methodName)
                        && result != null) {
                    resultCount = (Integer) result;
                } else if ("query".equals(methodName)
                        && result != null) {
                    resultCount = ((List) result).size();
                } else {
                    //batch
                }

                //写日志
                logService.writeSqlLog(sql, param, costTime, resultCount, exception);
            } catch (Exception ex) {

            }

        }
    }

    private Object proceed(Invocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

}