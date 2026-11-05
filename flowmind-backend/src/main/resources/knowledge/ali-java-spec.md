## SQL 注入

【阿里 Java 规约】SQL 操作使用 PreparedStatement 预编译参数绑定，禁止拼接外部输入。

## 空指针

【阿里 Java 规约】明确参数可空性，访问对象前校验空值，集合返回空集合而非 null。

## 异常与资源

【阿里 Java 规约】使用 try-with-resources 关闭资源；记录异常上下文，禁止吞掉异常。
