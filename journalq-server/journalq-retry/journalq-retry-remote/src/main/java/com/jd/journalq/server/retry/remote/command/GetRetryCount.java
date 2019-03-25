package com.jd.journalq.server.retry.remote.command;

import com.jd.journalq.network.command.CommandType;
import com.jd.journalq.network.transport.command.JMQPayload;

/**
 * 获取重试条数
 */
public class GetRetryCount extends JMQPayload {
    // 主题
    private String topic;
    // 应用
    private String app;

    @Override
    public int type() {
        return CommandType.GET_RETRY_COUNT;
    }

    public GetRetryCount topic(final String topic) {
        setTopic(topic);
        return this;
    }

    public GetRetryCount app(final String app) {
        setApp(app);
        return this;
    }

    public String getTopic() {
        return this.topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getApp() {
        return this.app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GetRetry{");
        sb.append("topic='").append(topic).append('\'');
        sb.append(", app='").append(app).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        GetRetryCount getRetry = (GetRetryCount) o;

        if (app != null ? !app.equals(getRetry.app) : getRetry.app != null) {
            return false;
        }
        if (topic != null ? !topic.equals(getRetry.topic) : getRetry.topic != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (topic != null ? topic.hashCode() : 0);
        result = 31 * result + (app != null ? app.hashCode() : 0);
        return result;
    }


}