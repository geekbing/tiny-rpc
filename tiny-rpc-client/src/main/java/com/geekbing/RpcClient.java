package com.geekbing;

import com.geekbing.bean.RpcRequest;
import com.geekbing.bean.RpcResponse;
import com.geekbing.codec.RpcDecoder;
import com.geekbing.codec.RpcEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RPC客户端（用于创建 RPC 服务代理）
 *
 * @author bing
 */
@Component
public class RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    @Resource
    private ServiceDiscovery serviceDiscovery;

    private ConcurrentMap<String, RpcResponse> responseMap = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T create(final Class<T> interfaceClass) {
        // 创建动态代理对象
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, (proxy, method, args) -> {
            // 创建RPC请求对象
            RpcRequest request = new RpcRequest();
            request.setRequestId(UUID.randomUUID().toString());
            request.setInterfaceName(method.getDeclaringClass().getName());
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());
            request.setParameters(args);
            // 获取RPC服务地址
            String serviceName = interfaceClass.getName();
            String serviceAddress = serviceDiscovery.discover(serviceName);
            logger.debug("discover service: {} = {}", serviceName, serviceAddress);
            if (serviceAddress == null || serviceAddress.isEmpty()) {
                throw new RuntimeException("server address is empty");
            }

            // 从RPC服务地址中解析主机名与端口号
            String[] array = serviceAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);
            // 发送RPC请求
            RpcResponse response = send(request, host, port);
            if (response == null) {
                logger.error("send request failure", new IllegalStateException("response is null"));
                return null;
            }
            if (response.hasException()) {
                logger.error("response has exception", response.getException());
                return null;
            }
            // 获取响应结果
            return response.getResult();
        });
    }

    private RpcResponse send(RpcRequest request, String host, int port) {
        // 单线程模式
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            // 创建 RPC 连接
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    // 编码 RPC 请求
                    pipeline.addLast(new RpcEncoder(RpcRequest.class));
                    // 解码 RPC 响应
                    pipeline.addLast(new RpcDecoder(RpcResponse.class));
                    // 处理 RPC 响应
                    pipeline.addLast(new RpcClientHandler(responseMap));
                }
            });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            // 写入RPC请求对象
            Channel channel = future.channel();
            channel.writeAndFlush(request).sync();
            channel.closeFuture().sync();
            // 获取RPC响应对象
            return responseMap.get(request.getRequestId());
        } catch (Exception e) {
            logger.error("client exception", e);
            return null;
        } finally {
            // 关闭RPC连接
            group.shutdownGracefully();
            // 移除请求编号与相应对象之间的映射关系
            responseMap.remove(request.getRequestId());
        }
    }
}
