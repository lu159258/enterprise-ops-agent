# 服务无法启动排查手册

## 现象

服务启动失败，日志出现 Required configuration missing 或 ClassNotFoundException。

## 常见原因

1. 配置中心必填项缺失（如数据库 URL、密钥）。
2. 依赖的中间件不可达：MySQL、Redis 连接超时。
3. 镜像内依赖冲突，启动即抛 NoClassDefFoundError。

## 处理步骤

1. 核对启动参数与配置中心 diff。
2. 用 telnet 检查依赖中间件端口连通性。
3. 对比上一个成功版本的镜像与依赖清单。