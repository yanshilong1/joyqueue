package com.jd.journalq.nsr.network.codec;

import com.jd.journalq.domain.PartitionGroup;

import com.jd.journalq.nsr.network.NsrPayloadCodec;
import com.jd.journalq.nsr.network.command.CreatePartitionGroup;
import com.jd.journalq.nsr.network.command.RemovePartitionGroup;
import com.jd.journalq.network.serializer.Serializer;
import com.jd.journalq.network.transport.command.Header;
import com.jd.journalq.network.transport.command.Types;
import com.jd.journalq.nsr.network.command.NsrCommandType;
import com.jd.journalq.nsr.network.command.OperatePartitionGroup;
import com.jd.journalq.nsr.network.command.UpdatePartitionGroup;
import io.netty.buffer.ByteBuf;

/**
 * @author wylixiaobin
 * Date: 2018/10/11
 */
public class OperatePartitionGroupCodec implements NsrPayloadCodec<OperatePartitionGroup>, Types {
    private static final int[] types = new int[]{NsrCommandType.NSR_CREATE_PARTITIONGROUP,
            NsrCommandType.NSR_UPDATE_PARTITIONGROUP,
            NsrCommandType.NSR_REMOVE_PARTITIONGROUP,
            NsrCommandType.NSR_LEADERCHANAGE_PARTITIONGROUP};

    @Override
    public Object decode(Header header, ByteBuf buffer) throws Exception {
        PartitionGroup group = Serializer.readPartitionGroup(buffer);
        boolean rollback = buffer.readBoolean();
        int cmdType = header.getType();
        if (cmdType == NsrCommandType.NSR_CREATE_PARTITIONGROUP) {
            return new CreatePartitionGroup(group, rollback);
        } else if (cmdType == NsrCommandType.NSR_UPDATE_PARTITIONGROUP || cmdType == NsrCommandType.NSR_LEADERCHANAGE_PARTITIONGROUP) {
            return new UpdatePartitionGroup(group, rollback);
        } else if (cmdType == NsrCommandType.NSR_REMOVE_PARTITIONGROUP) {
            return new RemovePartitionGroup(group, rollback);
        } else if (cmdType == NsrCommandType.NSR_LEADERCHANAGE_PARTITIONGROUP) {
            return new UpdatePartitionGroup(group,rollback);
        }
        return null;
    }

    @Override
    public int[] types() {
        return types;
    }

    @Override
    public void encode(OperatePartitionGroup payload, ByteBuf buffer) throws Exception {
        PartitionGroup partitionGroup = payload.getPartitionGroup();
        Serializer.write(partitionGroup, buffer);
        buffer.writeBoolean(payload.isRollback());
    }
}
