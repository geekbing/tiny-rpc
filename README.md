# tiny-rpc

## tiny-rpc-common 模块
项目的公共模块，被其他三个模块所依赖。
tiny-rpc-common 模块定义了RPC的请求和响应，提供了编码RPC请求和解码RPC响应的功能。
同时对 Protostuff 的序列化和反序列化进行了封装。

## tiny-rpc-registry 模块
项目的服务注册和发现模块。对 ZooKeeper 进行了简单的封装，提供了服务的注册和发现功能。

## tiny-rpc-client 模块
项目的客户端模块。包含调用RPC请求和处理RPC响应的功能。对Netty进行了简单的封装。

## tiny-rpc-server 模块
项目的服务端模块。包含启动RPC服务和对RPC请求进行处理的功能。同样对Netty进行了简单的封装。