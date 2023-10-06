package com.sumologic.jenkins.jenkinssumologicplugin.model;

import com.sumologic.jenkins.jenkinssumologicplugin.PluginDescriptorImpl;

import java.io.Serializable;

public class PluginConfiguration implements Serializable {
    protected static final long serialVersionUID = 1L;

    private String sumoLogicEndpoint;
    private String queryPortal;
    private String sourceCategory;
    private String metricDataPrefix;
    private boolean auditLogEnabled;
    private boolean keepOldConfigData;
    private boolean metricDataEnabled;
    private boolean periodicLogEnabled;
    private boolean jobStatusLogEnabled;
    private boolean jobConsoleLogEnabled;
    private boolean scmLogEnabled;
 // Proxy settings
    private boolean enableProxy = false;
    private String proxyHost = "";
    private int proxyPort = -1;
    private boolean enableProxyAuth = false;
    private String proxyAuthUsername = "";
    private String proxyAuthPassword = "";

    public PluginConfiguration(PluginDescriptorImpl pluginDescriptor) {
        this.sumoLogicEndpoint = pluginDescriptor.getUrl();
        this.queryPortal = pluginDescriptor.getQueryPortal();
        this.sourceCategory = pluginDescriptor.getSourceCategory();
        this.metricDataPrefix = pluginDescriptor.getMetricDataPrefix();
        this.auditLogEnabled = pluginDescriptor.isAuditLogEnabled();
        this.keepOldConfigData = pluginDescriptor.isKeepOldConfigData();
        this.metricDataEnabled = pluginDescriptor.isMetricDataEnabled();
        this.periodicLogEnabled = pluginDescriptor.isPeriodicLogEnabled();
        this.jobStatusLogEnabled = pluginDescriptor.isJobStatusLogEnabled();
        this.jobConsoleLogEnabled = pluginDescriptor.isJobConsoleLogEnabled();
        this.scmLogEnabled = pluginDescriptor.isScmLogEnabled();
        this.enableProxy=pluginDescriptor.getEnableProxy();
        this.proxyHost=pluginDescriptor.getProxyHost();
        this.proxyPort=pluginDescriptor.getProxyPort();
        this.enableProxyAuth=pluginDescriptor.getEnableProxyAuth();
        this.proxyAuthUsername=pluginDescriptor.getProxyAuthUsername();
        this.proxyAuthPassword=pluginDescriptor.getProxyAuthPassword();
    }

    public String getSumoLogicEndpoint() {
        return sumoLogicEndpoint;
    }

    public void setSumoLogicEndpoint(String sumoLogicEndpoint) {
        this.sumoLogicEndpoint = sumoLogicEndpoint;
    }

    public String getQueryPortal() {
        return queryPortal;
    }

    public void setQueryPortal(String queryPortal) {
        this.queryPortal = queryPortal;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public String getMetricDataPrefix() {
        return metricDataPrefix;
    }

    public void setMetricDataPrefix(String metricDataPrefix) {
        this.metricDataPrefix = metricDataPrefix;
    }

    public boolean isAuditLogEnabled() {
        return auditLogEnabled;
    }

    public void setAuditLogEnabled(boolean auditLogEnabled) {
        this.auditLogEnabled = auditLogEnabled;
    }

    public boolean isKeepOldConfigData() {
        return keepOldConfigData;
    }

    public void setKeepOldConfigData(boolean keepOldConfigData) {
        this.keepOldConfigData = keepOldConfigData;
    }

    public boolean isMetricDataEnabled() {
        return metricDataEnabled;
    }

    public void setMetricDataEnabled(boolean metricDataEnabled) {
        this.metricDataEnabled = metricDataEnabled;
    }

    public boolean isPeriodicLogEnabled() {
        return periodicLogEnabled;
    }

    public void setPeriodicLogEnabled(boolean periodicLogEnabled) {
        this.periodicLogEnabled = periodicLogEnabled;
    }

    public boolean isJobStatusLogEnabled() {
        return jobStatusLogEnabled;
    }

    public void setJobStatusLogEnabled(boolean jobStatusLogEnabled) {
        this.jobStatusLogEnabled = jobStatusLogEnabled;
    }

    public boolean isJobConsoleLogEnabled() {
        return jobConsoleLogEnabled;
    }

    public void setJobConsoleLogEnabled(boolean jobConsoleLogEnabled) {
        this.jobConsoleLogEnabled = jobConsoleLogEnabled;
    }

    public boolean isScmLogEnabled() {
        return scmLogEnabled;
    }

    public void setScmLogEnabled(boolean scmLogEnabled) {
        this.scmLogEnabled = scmLogEnabled;
    }

	public String getProxyAuthPassword() {
		return proxyAuthPassword;
	}

	public void setProxyAuthPassword(String proxyAuthPassword) {
		this.proxyAuthPassword = proxyAuthPassword;
	}

	public String getProxyAuthUsername() {
		return proxyAuthUsername;
	}

	public void setProxyAuthUsername(String proxyAuthUsername) {
		this.proxyAuthUsername = proxyAuthUsername;
	}

	public boolean isEnableProxyAuth() {
		return enableProxyAuth;
	}

	public void setEnableProxyAuth(boolean enableProxyAuth) {
		this.enableProxyAuth = enableProxyAuth;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public boolean isEnableProxy() {
		return enableProxy;
	}

	public void setEnableProxy(boolean enableProxy) {
		this.enableProxy = enableProxy;
	}
}
