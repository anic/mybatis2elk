package com.demo.serivce;

public interface ISqlLogService {

    void writeSqlLog(String mapperMethodName, String cmdType, long costTime, Integer resultCount, String exception);
}
