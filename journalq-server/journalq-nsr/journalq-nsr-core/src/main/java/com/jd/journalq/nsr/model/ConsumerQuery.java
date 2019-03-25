package com.jd.journalq.nsr.model;

import com.jd.journalq.model.Query;

import java.util.List;

public class ConsumerQuery implements Query {
    /**
     * 应用
     */
    private String app;
    /**
     * 主题
     */
    private String topic;
    /**
     * 命名空间
     */
    private String namespace;

    private String referer;
    /**
     * 客户端类型
     */
    private byte clientType = -1;

    private List<String> appList;

    public ConsumerQuery() {
    }

    public ConsumerQuery(String topic, String namespace) {
        this.topic = topic;
        this.namespace = namespace;
    }

    public ConsumerQuery(String app) {
        this.app = app;
    }

    public ConsumerQuery(String app, String topic, String namespace) {
        this.app = app;
        this.topic = topic;
        this.namespace = namespace;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public byte getClientType() {
        return clientType;
    }

    public void setClientType(byte clientType) {
        this.clientType = clientType;
    }

    public List<String> getAppList() {
        return appList;
    }

    public void setAppList(List<String> appList) {
        this.appList = appList;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }
}
