package com.agentops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ops.docker")
public class OpsDockerProperties {

    private boolean enabled;
    private String initCommand;
    private String bootstrapCommand;
    private String dbInitCommand;
    private String startXxlCommand;
    private int timeoutSeconds = 120;
    private String sshHost = "10.10.10.1";
    private int sshPort = 22;
    private String sshUsername = "root";
    private int sshConnectTimeoutMs = 3000;
    private String sqlRemoteDir = "/opt/agent-ops/sql";
    private String scriptRemoteDir = "/opt/agent-ops/scripts";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getInitCommand() { return initCommand; }
    public void setInitCommand(String initCommand) { this.initCommand = initCommand; }
    public String getBootstrapCommand() { return bootstrapCommand; }
    public void setBootstrapCommand(String bootstrapCommand) { this.bootstrapCommand = bootstrapCommand; }
    public String getDbInitCommand() { return dbInitCommand; }
    public void setDbInitCommand(String dbInitCommand) { this.dbInitCommand = dbInitCommand; }
    public String getStartXxlCommand() { return startXxlCommand; }
    public void setStartXxlCommand(String startXxlCommand) { this.startXxlCommand = startXxlCommand; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getSshHost() { return sshHost; }
    public void setSshHost(String sshHost) { this.sshHost = sshHost; }
    public int getSshPort() { return sshPort; }
    public void setSshPort(int sshPort) { this.sshPort = sshPort; }
    public String getSshUsername() { return sshUsername; }
    public void setSshUsername(String sshUsername) { this.sshUsername = sshUsername; }
    public int getSshConnectTimeoutMs() { return sshConnectTimeoutMs; }
    public void setSshConnectTimeoutMs(int sshConnectTimeoutMs) { this.sshConnectTimeoutMs = sshConnectTimeoutMs; }
    public String getSqlRemoteDir() { return sqlRemoteDir; }
    public void setSqlRemoteDir(String sqlRemoteDir) { this.sqlRemoteDir = sqlRemoteDir; }
    public String getScriptRemoteDir() { return scriptRemoteDir; }
    public void setScriptRemoteDir(String scriptRemoteDir) { this.scriptRemoteDir = scriptRemoteDir; }
}
