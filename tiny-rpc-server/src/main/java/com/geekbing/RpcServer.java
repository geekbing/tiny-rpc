package com.geekbing;

import com.geekbing.bean.RpcRequest;
import com.geekbing.bean.RpcResponse;
import com.geekbing.codec.RpcDecoder;
import com.geekbing.codec.RpcEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC服务器（用于发布RPC服务）
 *
 * @author bing
 */
@Component
public class RpcServer implements ApplicationContextAware, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    /**
     * 存放服务名称与服务实例之间的映射关系
     */
    private Map<String, Object> handlerMap = new HashMap<>();

    @Value("${rpc.port}")
    private int port;

    @Resource
    private ServiceRegistry serviceRegistry;

    /**
     * 通过 ApplicationContext 对象的 getBeansWithAnnotation 方法来获取带有 @RpcService 注解的服务实例
     * 然后通过循环遍历每个服务实例，获取服务名称，并将服务名称与服务实例建立映射关系，存放入Map中，供后续使用
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 扫描带有 @RpcService 注解的服务实例
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(RpcService.class);
        if (serviceBeanMap != null && !serviceBeanMap.isEmpty()) {
            for (Object serviceBean : serviceBeanMap.values()) {
                RpcService rpcService = serviceBean.getClass().getAnnotation(RpcService.class);
                String serviceName = rpcService.value().getName();
                handlerMap.put(serviceName, serviceBean);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // bossGroup 用于控制并管理相关的childGroup
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        // childGroup 用于处理 RPC 请求
        EventLoopGroup childGroup = new NioEventLoopGroup();

        try {
            // 启动 RPC 服务
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, childGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    // 解码RPC请求
                    pipeline.addLast(new RpcDecoder(RpcRequest.class));
                    // 编码RPC响应
                    pipeline.addLast(new RpcEncoder(RpcResponse.class));
                    // 处理RPC请求
                    pipeline.addLast(new RpcServerHandler(handlerMap));
                }
            });
            ChannelFuture future = bootstrap.bind(port).sync();
            logger.debug("server started, listening on {}", port);

            // 注册 RPC 服务地址
            String serviceAddress = InetAddress.getLocalHost().getHostAddress() + ":" + port;
            for (String interfaceName : handlerMap.keySet()) {
                // 注册 RPC 服务到 zookeeper
                serviceRegistry.register(interfaceName, serviceAddress);
                logger.debug("register service: {} => {}", interfaceName, serviceAddress);
            }

            // 释放资源
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("server exception", e);
        } finally {
            // 关闭 RPC 服务
            childGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
