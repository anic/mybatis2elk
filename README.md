# combine Mybatis logs to elk

mybatis打出的日志是这样的：
```
2018-05-18 17:17:09,870|DEBUG|c.i.c.d.m.B.listByKeys|debug|dao|[http-nio-8090-exec-3]|xlk|icu|127.0.0.1|133455757jhbr2ty5|75007|==>  Preparing: SELECT * FROM department WHERE id IN ( ? ) 
2018-05-18 17:17:09,871|DEBUG|c.i.c.d.m.B.listByKeys|debug|dao|[http-nio-8090-exec-3]|xlk|icu|127.0.0.1|133455757jhbr2ty5|75007|==> Parameters: 3647(Integer)
2018-05-18 17:17:09,875|DEBUG|c.i.c.d.m.B.listByKeys|debug|dao|[http-nio-8090-exec-3]|xlk|icu|127.0.0.1|133455757jhbr2ty5|75007|<==      Total: 1
```
但是这3条日志是有3行记录，想要将3行记录合并为1条推送到ELK十分困难。这篇[文章](https://blog.csdn.net/hfismyangel/article/details/80367528)有对原理进行分析，虽然十分困难，但这里还是找到了解决方案。

1. 编写拦截器`SqlLogInterceptor`，对`StatementHandler`的`preapre`方法进行拦截
该方法的原型，其中`Connection`是`ConnectionLogger`对象，`Statement`是`PreparedStatementLogger`对象
```
Statement prepare(Connection connection, Integer transactionTimeout)
      throws SQLException;
```
如果将准备Statement的过程，换成自定义的logger，就可以将将日志收集起来

核心代码
```
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
```

2. 对`StatementHandler`的`update`、`query`和`batch`进行拦截，记录日志

```
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
```
