package com.jd.journalq.broker.jmq.handler;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import com.jd.journalq.broker.jmq.converter.BrokerNodeConverter;
import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.BrokerContextAware;
import com.jd.journalq.broker.config.BrokerConfig;
import com.jd.journalq.broker.helper.SessionHelper;
import com.jd.journalq.broker.jmq.JMQCommandHandler;
import com.jd.journalq.domain.Broker;
import com.jd.journalq.domain.Consumer;
import com.jd.journalq.domain.DataCenter;
import com.jd.journalq.domain.PartitionGroup;
import com.jd.journalq.domain.Producer;
import com.jd.journalq.domain.TopicConfig;
import com.jd.journalq.domain.TopicName;
import com.jd.journalq.exception.JMQCode;
import com.jd.journalq.network.command.BooleanAck;
import com.jd.journalq.network.command.FetchCluster;
import com.jd.journalq.network.command.FetchClusterAck;
import com.jd.journalq.network.command.JMQCommandType;
import com.jd.journalq.network.command.Topic;
import com.jd.journalq.network.command.TopicPartition;
import com.jd.journalq.network.command.TopicPartitionGroup;
import com.jd.journalq.network.domain.BrokerNode;
import com.jd.journalq.network.session.Connection;
import com.jd.journalq.network.transport.Transport;
import com.jd.journalq.network.transport.command.Command;
import com.jd.journalq.network.transport.command.Type;
import com.jd.journalq.nsr.NameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * FetchClusterHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/30
 */
public class FetchClusterHandler implements JMQCommandHandler, Type, BrokerContextAware {

    protected static final Logger logger = LoggerFactory.getLogger(FetchClusterHandler.class);

    private BrokerConfig brokerConfig;
    private NameService nameService;
    private BrokerContext brokerContext;

    @Override
    public void setBrokerContext(BrokerContext brokerContext) {
        this.brokerConfig = brokerContext.getBrokerConfig();
        this.nameService = brokerContext.getNameService();
        this.brokerContext = brokerContext;
    }

    @Override
    public Command handle(Transport transport, Command command) {
        FetchCluster fetchCluster = (FetchCluster) command.getPayload();
        Connection connection = SessionHelper.getConnection(transport);

        if (connection == null || !connection.isAuthorized(fetchCluster.getApp())) {
            logger.warn("connection is not exists, transport: {}", transport);
            return BooleanAck.build(JMQCode.FW_CONNECTION_NOT_EXISTS.getCode());
        }

        Map<String, Topic> topics = Maps.newHashMapWithExpectedSize(fetchCluster.getTopics().size());
        Map<Integer, BrokerNode> brokers = Maps.newHashMap();

        for (String topicId : fetchCluster.getTopics()) {
            Topic topic = getTopicMetadata(connection, topicId, fetchCluster.getApp(), brokers);
            topics.put(topicId, topic);
        }

        FetchClusterAck fetchClusterAck = new FetchClusterAck();
        fetchClusterAck.setTopics(topics);
        fetchClusterAck.setBrokers(brokers);

        // TODO 临时日志
        logger.debug("fetch cluster, address: {}, topics: {}, app: {}, metadata: {}",
                transport, fetchCluster.getTopics(), fetchCluster.getApp(), JSON.toJSONString(fetchClusterAck));

        return new Command(fetchClusterAck);
    }

    protected Topic getTopicMetadata(Connection connection, String topic, String app, Map<Integer, BrokerNode> brokers) {
        TopicName topicName = TopicName.parse(topic);
        TopicConfig topicConfig = nameService.getTopicConfig(topicName);

        Topic result = new Topic();
        result.setTopic(topic);

        if (topicConfig == null) {
            logger.warn("topic not exist, topic: {}, app: {}", topic, app);
            result.setCode(JMQCode.FW_TOPIC_NOT_EXIST);
            return result;
        }

        Producer producer = nameService.getProducerByTopicAndApp(topicName, app);
        Consumer consumer = nameService.getConsumerByTopicAndApp(topicName, app);

        if (producer == null && consumer == null) {
            logger.warn("topic policy not exist, topic: {}, app: {}", topic, app);
            result.setCode(JMQCode.CN_NO_PERMISSION);
            return result;
        }

        if (producer != null) {
            if (producer.getProducerPolicy() == null) {
                result.setProducerPolicy(brokerContext.getProducerPolicy());
            } else {
                result.setProducerPolicy(producer.getProducerPolicy());
            }
        }

        //TODO
        if (consumer != null) {
            if (consumer.getConsumerPolicy() == null) {
                result.setConsumerPolicy(brokerContext.getConsumerPolicy());
            } else {
                result.setConsumerPolicy(consumer.getConsumerPolicy());
            }
        }

        result.setCode(JMQCode.SUCCESS);
        result.setPartitionGroups(convertTopicPartitionGroups(connection, topicConfig.getPartitionGroups().values(), brokers));
        result.setType(topicConfig.getType());
        return result;
    }

    protected Map<Integer, TopicPartitionGroup> convertTopicPartitionGroups(Connection connection, Collection<PartitionGroup> partitionGroups, Map<Integer, BrokerNode> brokers) {
        Map<Integer, TopicPartitionGroup> result = Maps.newLinkedHashMap();
        for (PartitionGroup partitionGroup : partitionGroups) {
            TopicPartitionGroup topicPartitionGroup = convertTopicPartitionGroup(connection, partitionGroup, brokers);
            if (topicPartitionGroup != null) {
                result.put(partitionGroup.getGroup(), topicPartitionGroup);
            }
        }
        return result;
    }

    protected TopicPartitionGroup convertTopicPartitionGroup(Connection connection, PartitionGroup partitionGroup, Map<Integer, BrokerNode> brokers) {
        Map<Short, TopicPartition> partitions = Maps.newLinkedHashMap();

        Broker leaderBroker = partitionGroup.getLeaderBroker();
        if (leaderBroker != null) {
            DataCenter brokerDataCenter = nameService.getDataCenter(leaderBroker.getIp());
            brokers.put(partitionGroup.getLeader(), BrokerNodeConverter.convertBrokerNode(leaderBroker, brokerDataCenter, connection.getRegion()));
        }

        for (Short partition : partitionGroup.getPartitions()) {
            partitions.put(partition, convertTopicPartition(partitionGroup, partition));
        }

        TopicPartitionGroup result = new TopicPartitionGroup();
        result.setId(partitionGroup.getGroup());
        result.setLeader(partitionGroup.getLeader());
        result.setPartitions(partitions);
        return result;
    }

    protected TopicPartition convertTopicPartition(PartitionGroup partitionGroup, short partition) {
        TopicPartition result = new TopicPartition();
        result.setId(partition);
        return result;
    }

    @Override
    public int type() {
        return JMQCommandType.FETCH_CLUSTER.getCode();
    }
}