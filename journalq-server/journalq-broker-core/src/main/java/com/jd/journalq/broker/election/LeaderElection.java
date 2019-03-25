package com.jd.journalq.broker.election;

import com.jd.journalq.broker.cluster.ClusterManager;
import com.jd.journalq.broker.election.command.AppendEntriesRequest;
import com.jd.journalq.broker.replication.ReplicaGroup;
import com.jd.journalq.domain.TopicName;
import com.jd.journalq.network.transport.command.Command;
import com.jd.journalq.store.replication.ReplicableStore;
import com.jd.journalq.toolkit.concurrent.EventBus;
import com.jd.journalq.toolkit.service.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


/**
 * author: zhuduohui
 * email: zhuduohui@jd.com
 * date: 2018/8/11
 */
public abstract class LeaderElection extends Service {
    private static Logger logger = LoggerFactory.getLogger(LeaderElection.class);

    protected ElectionConfig electionConfig;
    protected TopicPartitionGroup topicPartitionGroup;
    protected ElectionManager electionManager;
    protected ClusterManager clusterManager;

    protected int leaderId = ElectionNode.INVALID_NODE_ID;
    protected int localNodeId = ElectionNode.INVALID_NODE_ID;

    protected EventBus<ElectionEvent> electionEventManager;
    protected ElectionMetadataManager electionMetadataManager;

    protected ReplicaGroup replicaGroup;
    protected ReplicableStore replicableStore;

    /**
     * 获取参与选举的所有节点
     * @return 所有节点
     */
    public abstract Collection<DefaultElectionNode> getAllNodes();

    /**
     * 获取leader节点id
     */
    public int getLeaderId() {
        return leaderId;
    }

    public abstract void setLeaderId(int leaderId) throws Exception;

    /**
     * 选举集群增加节点
     * @param node 增加的节点
     */
    public void addNode(DefaultElectionNode node) {
        replicaGroup.addNode(node);
    }

    /**
     * 删除集群增加节点
     * @param brokerId 待删除的broker id
     */
    public void removeNode(int brokerId) {
        replicaGroup.removeNode(brokerId);
    }

    /**
     * 当前节点是否是leader
     * @return 是否是leader
     */
    public boolean isLeader() {
        return leaderId == localNodeId;
    }

    /**
     * 获取复制组
     * @return 复制组
     */
    public ReplicaGroup getReplicaGroup() {
        return replicaGroup;
    }

    /**
     * 更新元数据
     * @param leaderId leader id
     * @param term 任期
     */
    void updateMetadata(int leaderId, int term) {
        Set<Integer> isrId = new HashSet<>();

        logger.info("Leader report, topic is {}, group id is {}, leader is {}, term is {}",
                topicPartitionGroup.getTopic(), topicPartitionGroup.getPartitionGroupId(),
                leaderId, term);

        try {
            clusterManager.leaderReport(TopicName.parse(topicPartitionGroup.getTopic()),
                    topicPartitionGroup.getPartitionGroupId(), leaderId, isrId, term);
        } catch (Exception e) {
            logger.warn("Partition group {}/node {} report leader fail",
                    topicPartitionGroup, localNodeId, e);
        }
    }

    /**
     * 处理添加记录请求
     * @param request 添加记录请求
     * @return 返回命令
     */
    public abstract Command handleAppendEntriesRequest(AppendEntriesRequest request);

}
