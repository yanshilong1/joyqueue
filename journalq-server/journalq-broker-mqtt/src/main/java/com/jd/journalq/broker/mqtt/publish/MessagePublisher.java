package com.jd.journalq.broker.mqtt.publish;

import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.buffer.Serializer;
import com.jd.journalq.broker.consumer.Consume;
import com.jd.journalq.broker.consumer.model.PullResult;
import com.jd.journalq.broker.mqtt.cluster.MqttConnectionManager;
import com.jd.journalq.broker.mqtt.connection.MqttConnection;
import com.jd.journalq.broker.mqtt.session.MqttSession;
import com.jd.journalq.broker.producer.Produce;
import com.jd.journalq.domain.QosLevel;
import com.jd.journalq.exception.JMQException;
import com.jd.journalq.message.BrokerMessage;
import com.jd.journalq.network.session.Consumer;
import com.jd.journalq.network.session.Producer;
import com.jd.journalq.broker.mqtt.util.MqttMessageSerializer;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

/**
 * @author majun8
 */
public class MessagePublisher {
    private static final Logger LOG = LoggerFactory.getLogger(MessagePublisher.class);
    private Produce produce;
    private Consume consume;
    private MqttConnectionManager connectionManager;

    public MessagePublisher(BrokerContext brokerContext, MqttConnectionManager connectionManager) {
        this.produce = brokerContext.getProduce();
        this.consume = brokerContext.getConsume();
        this.connectionManager = connectionManager;
    }

    public void publishMessage(Producer producer, Channel client, MqttPublishMessage publishMessage) throws JMQException {
        final MqttQoS qos = publishMessage.fixedHeader().qosLevel();
        final int packageID = publishMessage.variableHeader().packetId();

        produce.putMessageAsync(
                producer,
                Collections.singletonList(MqttMessageSerializer.convertToBrokerMsg(client, publishMessage)),
                QosLevel.RECEIVE,
                event -> processPublishResult(client, qos, packageID)
        );
    }

    private void processPublishResult(Channel client, MqttQoS qos, int packageID) {
        switch (qos) {
            case AT_MOST_ONCE:
                break;
            case AT_LEAST_ONCE:
                sendPubAck(client, packageID);
                break;
            case EXACTLY_ONCE:
                // not implement rel and complete ack, store message only
                break;
            case FAILURE:
                // ignore
                LOG.info("Received failure qos message, ignore...");
                break;
        }
    }

    private void sendPubAck(Channel client, Integer packageID) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("发送PubAck消息给客户端");
        }
        try {
            MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttMessage pubAckMessage = MqttMessageFactory.newMessage(
                    mqttFixedHeader,
                    MqttMessageIdVariableHeader.from(packageID),
                    null);
            client.writeAndFlush(pubAckMessage);
        } catch (Throwable th) {
            LOG.error("Send pubAck error!", th);
            client.close().addListener(CLOSE_ON_FAILURE);
        }
    }

    public void publish2Subscriber(String name, String clientID, MqttSession session, Consumer consumer, int qos) throws Exception {
        PullResult result = consume.getMessage(
                consumer,
                1,
                1000 * 60 * 2
        );
        String topicName = result.getTopic();
        List<ByteBuffer> buffers = result.getBuffers();
        if (buffers != null && buffers.size() > 0) {
            BrokerMessage brokerMessage = Serializer.readBrokerMessage(buffers.get(0));
            MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(
                    MqttMessageType.PUBLISH,
                    false,
                    MqttQoS.valueOf(MqttMessageSerializer.getLowerQos(MqttMessageSerializer.readExtension(brokerMessage), qos)),
                    false,
                    0
            );
            int packageId = session.getMessageAcknowledgedZone().acquireAcknowledgedPosition(brokerMessage);
            MqttPublishMessage publishMsg = (MqttPublishMessage) MqttMessageFactory.newMessage(
                    mqttFixedHeader,
                    new MqttPublishVariableHeader(topicName, packageId),
                    Unpooled.wrappedBuffer(brokerMessage.getByteBody()));

            boolean isActive = connectionManager.isConnected(clientID);
            if (isActive) {
                MqttConnection connection = connectionManager.getConnection(clientID);
                Channel channel = connection.getChannel();
                if (channel.isActive() && channel.isOpen()) {
                    channel.writeAndFlush(publishMsg).addListener((ChannelFutureListener) channelFuture -> {
                        if (channelFuture.isSuccess()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("推送消息成功: {}", publishMsg);
                            }
                        } else {
                            LOG.error("publish message error, thread: <{}>, clientID: <{}>, message: <{}>, cause: <{}>", name, clientID, brokerMessage, channelFuture.cause());
                            throw new Exception(channelFuture.cause());
                        }
                    });
                }
            }
        }
    }
}
