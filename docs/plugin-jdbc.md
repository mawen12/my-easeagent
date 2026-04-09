# Plugin Jdbc

## Plugin

- `JdbcDataSourceMetricPlugin`
- `JdbcConnectionMetricPlugin`
- `JdbcTracingPlugin`
- `JdbcRedirectPlugin`

## Points

- `HikariDataSourceAdvice` 拦截 HikariConfig 的 set* 方法
- `JdbcDataSourceAdvice` 拦截 DataSource 的 getConnection 方法
- `JdbcConnectionAdvice` 拦截 Connection 的 prepareStatement、prepareCall、createStatement 方法
- `JdbcStatementAdvice` 拦截 Statement 的 execute*、addBatch、clearBatch 方法 

## Interceptor

- `HikariSetPropertyInterceptor` 
  1. 在 HikariConfig 的 set* 方法（`HikariDataSourceAdvice`）之前执行，
  2. 写入 agent 配置中的数据库地址和用户信息，实现参数的覆盖

- `JdbcDataSourceMetricInterceptor`
  1. 在 DataSource 的 getConnection 方法（`JdbcDataSourceAdvice`）之后执行，
  2. 记录数据源的连接的获取次数、获取时间、获取失败次数等指标

- `JdbcConPrepareOrCreateStmInterceptor`
  1. 在 Connection 的 prepareStatement、prepareCall、createStatement 方法（`JdbcConnectionAdvice`）之后执行，
  2. 记录 SQL 语句，并将其封装到 SqlInfo 中，然后借助动态字段写入大 Statement 。

- `` 
