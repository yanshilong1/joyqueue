package com.jd.journalq.broker.mqtt.network;

import com.jd.journalq.broker.mqtt.transport.MqttCommandInvocation;
import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.mqtt.handler.MqttHandlerDispatcher;
import com.jd.journalq.broker.network.protocol.support.DefaultProtocolHandlerPipeline;
import com.jd.journalq.network.handler.ConnectionHandler;
import com.jd.journalq.network.protocol.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;

import java.util.List;

/**
 * @author majun8
 */
public class MqttOverWebsocketProtocolHandlerPipeline extends ChannelInitializer {
    private static final String MQTT_SUBPROTOCOL_CSV_LIST = "mqtt, mqttv3.1, mqttv3.1.1";

    private Protocol protocol;
    private BrokerContext brokerContext;

    public MqttOverWebsocketProtocolHandlerPipeline(Protocol protocol, ChannelHandler channelHandler, BrokerContext brokerContext) {
        this.protocol = protocol;
        this.brokerContext = brokerContext;
        if (channelHandler instanceof DefaultProtocolHandlerPipeline) {
            DefaultProtocolHandlerPipeline handlerPipeline = (DefaultProtocolHandlerPipeline) channelHandler;
            // todo
        }
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        channel.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new WebSocketServerProtocolHandler("/mqtt", MQTT_SUBPROTOCOL_CSV_LIST))
                .addLast(new WebSocketFrameToByteBufDecoder())
                .addLast(new ByteBufToWebSocketFrameEncoder())
                .addLast(new MqttDecoder())
                .addLast(MqttEncoder.INSTANCE)
                .addLast(new ConnectionHandler())
                .addLast(newMqttCommandInvocation());
    }

    protected MqttCommandInvocation newMqttCommandInvocation() {
        return new MqttCommandInvocation(newMqttHandlerDispatcher());
    }

    protected MqttHandlerDispatcher newMqttHandlerDispatcher() {
        MqttHandlerDispatcher mqttHandlerDispatcher = new MqttHandlerDispatcher(protocol.createCommandHandlerFactory(), brokerContext);
        try {
            mqttHandlerDispatcher.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mqttHandlerDispatcher;
    }

    static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {

        @Override
        protected void decode(ChannelHandlerContext chc, BinaryWebSocketFrame frame, List<Object> out)
                throws Exception {
            // convert the frame to a ByteBuf
            ByteBuf bb = frame.content();
            // System.out.println("WebSocketFrameToByteBufDecoder decode - " +
            // ByteBufUtil.hexDump(bb));
            bb.retain();
            out.add(bb);
        }
    }

    static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
            // convert the ByteBuf to a WebSocketFrame
            BinaryWebSocketFrame result = new BinaryWebSocketFrame();
            // System.out.println("ByteBufToWebSocketFrameEncoder encode - " +
            // ByteBufUtil.hexDump(bb));
            result.content().writeBytes(bb);
            out.add(result);
        }
    }
}
