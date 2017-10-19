package com.geekbing.codec;

import com.geekbing.util.SerializationUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * RPC 解码器
 *
 * @author bing
 */
public class RpcDecoder extends ByteToMessageDecoder {
    /**
     * int类型所占的字节数。序列化的时候，会先发送一个int来表示数据的长度
     */
    private static final int INT_BYTE_LENGTH = 4;

    private Class<?> genericClass;

    public RpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < INT_BYTE_LENGTH) {
            return;
        }
        in.markReaderIndex();
        int dataLength = in.readInt();
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        byte[] data = new byte[dataLength];
        in.readBytes(data);
        // 反序列化
        out.add(SerializationUtil.deserialize(data, genericClass));
    }
}
