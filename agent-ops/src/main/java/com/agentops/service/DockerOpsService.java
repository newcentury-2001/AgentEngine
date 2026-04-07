package com.agentops.service;

import com.agentops.config.OpsDockerProperties;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DockerOpsService {

    private final OpsDockerProperties properties;

    public DockerOpsService(OpsDockerProperties properties) {
        this.properties = properties;
    }

    /**
     * 执行容器运维初始化（三步串行）。
     * <p>
     * 第一步：bootstrap，创建网络/卷并启动基础容器（PostgreSQL、Redis、MySQL、RocketMQ）。<br>
     * 第二步：db-init，执行服务器本地 SQL 文件，初始化 MySQL 与 PostgreSQL 业务表。<br>
     * 第三步：start-xxl，最后启动 xxl-job-admin。<br>
     * 任一步失败都会立即中断并抛出异常，避免后续步骤在不一致状态下继续执行。
     */
    public Map<String, Object> initContainers() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("docker init disabled");
        }
        List<StepCommand> steps = resolveInitSteps();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("docker init command not configured");
        }
        try {
            List<Map<String, Object>> stepResults = new ArrayList<>();
            for (StepCommand step : steps) {
                SshExecResult sshExecResult = executeRemote(step.command());
                int exit = sshExecResult.exitCode();
                String stdout = sshExecResult.stdout();
                String stderr = sshExecResult.stderr();
                if (exit != 0) {
                    throw new IllegalStateException("docker init failed at step [" + step.name() + "]: "
                            + (stderr.isBlank() ? ("exit=" + exit) : stderr.trim()));
                }
                Map<String, Object> oneStep = new LinkedHashMap<>();
                oneStep.put("step", step.name());
                oneStep.put("containerIds", stdout.lines().map(String::trim).filter(s -> !s.isBlank()).toList());
                oneStep.put("rawOutput", stdout);
                stepResults.add(oneStep);
            }
            return Map.of(
                    "message", "docker init success",
                    "targetHost", properties.getSshHost(),
                    "targetPort", properties.getSshPort(),
                    "durationSeconds", Duration.ofSeconds(Math.max(0, properties.getTimeoutSeconds())).toSeconds(),
                    "steps", stepResults
            );
        } catch (JSchException | IOException e) {
            throw new IllegalStateException("docker init remote exec error", e);
        }
    }

    /**
     * 上传 SQL 文件到远程服务器目录（默认 /opt/agent-ops/sql）。
     * <p>
     * 上传前会先创建目录，文件名默认使用原始文件名，也可由调用方指定 remoteName。
     */
    public Map<String, Object> uploadSqlFile(MultipartFile file, String remoteName) {
        return uploadFile(file, remoteName, ".sql", resolveSqlRemoteDir(), "sql upload success");
    }

    /**
     * 上传 Shell 脚本到远程服务器目录（默认 /opt/agent-ops/scripts）。
     */
    public Map<String, Object> uploadScriptFile(MultipartFile file, String remoteName) {
        return uploadFile(file, remoteName, ".sh", resolveScriptRemoteDir(), "script upload success");
    }

    /**
     * 一次性上传运维初始化所需的全部内置文件（scripts + sql）。
     */
    public Map<String, Object> uploadBundleFiles() {
        List<String[]> files = new ArrayList<>();
        files.add(new String[]{"ops-bundle/sql/init_xxl_mysql.sql", resolveSqlRemoteDir() + "/init_xxl_mysql.sql"});
        files.add(new String[]{"ops-bundle/sql/init_skill_pg.sql", resolveSqlRemoteDir() + "/init_skill_pg.sql"});
        files.add(new String[]{"ops-bundle/sql/init_slot_pg.sql", resolveSqlRemoteDir() + "/init_slot_pg.sql"});
        files.add(new String[]{"ops-bundle/sql/init_slot_seed_pg.sql", resolveSqlRemoteDir() + "/init_slot_seed_pg.sql"});
        files.add(new String[]{"ops-bundle/scripts/bootstrap.sh", resolveScriptRemoteDir() + "/bootstrap.sh"});
        files.add(new String[]{"ops-bundle/scripts/db-init.sh", resolveScriptRemoteDir() + "/db-init.sh"});
        files.add(new String[]{"ops-bundle/scripts/start-xxl.sh", resolveScriptRemoteDir() + "/start-xxl.sh"});
        return uploadBundleEntries(files, true, "bundle upload success");
    }

    /**
     * 上传内置 SQL 文件包（仅 sql）。
     */
    public Map<String, Object> uploadSqlBundleFiles() {
        List<String[]> files = new ArrayList<>();
        files.add(new String[]{"ops-bundle/sql/init_xxl_mysql.sql", resolveSqlRemoteDir() + "/init_xxl_mysql.sql"});
        files.add(new String[]{"ops-bundle/sql/init_skill_pg.sql", resolveSqlRemoteDir() + "/init_skill_pg.sql"});
        files.add(new String[]{"ops-bundle/sql/init_slot_pg.sql", resolveSqlRemoteDir() + "/init_slot_pg.sql"});
        files.add(new String[]{"ops-bundle/sql/init_slot_seed_pg.sql", resolveSqlRemoteDir() + "/init_slot_seed_pg.sql"});
        return uploadBundleEntries(files, false, "sql bundle upload success");
    }

    /**
     * 上传内置脚本文件包（仅 scripts）。
     */
    public Map<String, Object> uploadScriptBundleFiles() {
        List<String[]> files = new ArrayList<>();
        files.add(new String[]{"ops-bundle/scripts/bootstrap.sh", resolveScriptRemoteDir() + "/bootstrap.sh"});
        files.add(new String[]{"ops-bundle/scripts/db-init.sh", resolveScriptRemoteDir() + "/db-init.sh"});
        files.add(new String[]{"ops-bundle/scripts/start-xxl.sh", resolveScriptRemoteDir() + "/start-xxl.sh"});
        return uploadBundleEntries(files, true, "script bundle upload success");
    }

    private Map<String, Object> uploadBundleEntries(List<String[]> files, boolean chmodScripts, String msg) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("docker ops disabled");
        }
        String sqlDir = resolveSqlRemoteDir();
        String scriptDir = resolveScriptRemoteDir();

        Session session = null;
        try {
            session = openSession();
            executeRemoteWithSession(session, "mkdir -p " + sqlDir);
            executeRemoteWithSession(session, "mkdir -p " + scriptDir);

            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(Math.max(1000, properties.getSshConnectTimeoutMs()));
            List<String> uploaded = new ArrayList<>();
            try {
                for (String[] one : files) {
                    Resource resource = new ClassPathResource(one[0]);
                    if (!resource.exists()) {
                        throw new IllegalStateException("bundle resource not found: " + one[0]);
                    }
                    try (InputStream in = resource.getInputStream()) {
                        sftp.put(in, one[1], ChannelSftp.OVERWRITE);
                    }
                    uploaded.add(one[1]);
                }
            } finally {
                sftp.disconnect();
            }

            if (chmodScripts) {
                executeRemoteWithSession(session, "chmod +x " + scriptDir + "/*.sh");
            }
            return Map.of(
                    "message", msg,
                    "targetHost", properties.getSshHost(),
                    "targetPort", properties.getSshPort(),
                    "uploadedFiles", uploaded
            );
        } catch (JSchException | IOException | SftpException e) {
            throw new IllegalStateException("bundle upload failed", e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private Map<String, Object> uploadFile(
            MultipartFile file,
            String remoteName,
            String requiredSuffix,
            String remoteDir,
            String successMessage
    ) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("docker ops disabled");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String sourceName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String finalName = (remoteName == null || remoteName.isBlank()) ? sourceName : remoteName.trim();
        if (finalName.isBlank() || !finalName.toLowerCase().endsWith(requiredSuffix)) {
            throw new IllegalArgumentException("remote filename must end with " + requiredSuffix);
        }

        Session session = null;
        try {
            session = openSession();
            executeRemoteWithSession(session, "mkdir -p " + remoteDir);
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(Math.max(1000, properties.getSshConnectTimeoutMs()));
            String remotePath = remoteDir + "/" + finalName;
            try (InputStream in = file.getInputStream()) {
                sftp.put(in, remotePath, ChannelSftp.OVERWRITE);
            } finally {
                sftp.disconnect();
            }
            return Map.of(
                    "message", successMessage,
                    "targetHost", properties.getSshHost(),
                    "targetPort", properties.getSshPort(),
                    "remotePath", remotePath,
                    "size", file.getSize()
            );
        } catch (JSchException | IOException | SftpException e) {
            throw new IllegalStateException("file upload failed", e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private String resolveSqlRemoteDir() {
        return (properties.getSqlRemoteDir() == null || properties.getSqlRemoteDir().isBlank())
                ? "/opt/agent-ops/sql"
                : properties.getSqlRemoteDir().trim();
    }

    private String resolveScriptRemoteDir() {
        return (properties.getScriptRemoteDir() == null || properties.getScriptRemoteDir().isBlank())
                ? "/opt/agent-ops/scripts"
                : properties.getScriptRemoteDir().trim();
    }

    /**
     * 解析初始化步骤列表。
     * <p>
     * 优先读取分阶段命令（bootstrap/db-init/start-xxl），
     * 若都未配置，则回退到单条 initCommand。
     */
    private List<StepCommand> resolveInitSteps() {
        List<StepCommand> steps = new ArrayList<>();
        addStepIfPresent(steps, "bootstrap", properties.getBootstrapCommand());
        addStepIfPresent(steps, "db-init", properties.getDbInitCommand());
        addStepIfPresent(steps, "start-xxl", properties.getStartXxlCommand());
        if (!steps.isEmpty()) {
            return steps;
        }
        String fallback = properties.getInitCommand() == null ? "" : properties.getInitCommand().trim();
        if (!fallback.isBlank()) {
            steps.add(new StepCommand("init", fallback));
        }
        return steps;
    }

    private void addStepIfPresent(List<StepCommand> steps, String name, String command) {
        String cmd = command == null ? "" : command.trim();
        if (!cmd.isBlank()) {
            steps.add(new StepCommand(name, cmd));
        }
    }

    private SshExecResult executeRemote(String command) throws JSchException, IOException {
        Session session = null;
        try {
            session = openSession();
            return executeRemoteWithSession(session, command);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private SshExecResult executeRemoteWithSession(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        String wrapped = "sh -lc " + quoteForSingle(command);
        channel.setCommand(wrapped);
        channel.setInputStream(null);
        InputStream stdout = channel.getInputStream();
        InputStream stderr = channel.getErrStream();
        if (stderr == null) {
            stderr = channel.getExtInputStream();
        }
        channel.connect(Math.max(1000, properties.getSshConnectTimeoutMs()));

        long deadline = System.currentTimeMillis() + Math.max(5, properties.getTimeoutSeconds()) * 1000L;
        while (!channel.isClosed()) {
            if (System.currentTimeMillis() > deadline) {
                channel.disconnect();
                throw new IllegalStateException("docker init timeout");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.disconnect();
                throw new IllegalStateException("docker init interrupted", e);
            }
        }

        String out = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
        String err = stderr == null ? "" : new String(stderr.readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = channel.getExitStatus();
        channel.disconnect();
        return new SshExecResult(exitCode, out, err);
    }

    private Session openSession() throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(
                properties.getSshUsername(),
                properties.getSshHost(),
                properties.getSshPort()
        );
        session.setPassword(resolveSshPasswordFromEnv());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(Math.max(1000, properties.getSshConnectTimeoutMs()));
        return session;
    }

    private String quoteForSingle(String command) {
        return "'" + command.replace("'", "'\"'\"'") + "'";
    }

    private String resolveSshPasswordFromEnv() {
        String p1 = System.getenv("sshpwd");
        if (p1 != null && !p1.isBlank()) {
            return p1;
        }
        String p2 = System.getenv("SSHPWD");
        if (p2 != null && !p2.isBlank()) {
            return p2;
        }
        throw new IllegalStateException("ssh password env missing: sshpwd");
    }

    private record SshExecResult(int exitCode, String stdout, String stderr) {
    }

    private record StepCommand(String name, String command) {
    }
}
