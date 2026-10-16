package org.letspeppol.kyc.service.kbo;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringKboSftpClient implements KboSftpClient {

    @Value("${kbo.sftp.host}")
    private String host;

    @Value("${kbo.sftp.port:22}")
    private int port;

    @Value("${kbo.sftp.username}")
    private String username;

    @Value("${kbo.sftp.password}")
    private String password;

    /**
     * Optional path to a known_hosts file. When set, host-key verification is enforced
     * (StrictHostKeyChecking=yes), preventing man-in-the-middle attacks.
     */
    @Value("${kbo.sftp.known-hosts:}")
    private String knownHostsPath;

    /**
     * Optional inline known_hosts entry (a single line in known_hosts format,
     * e.g. "host ssh-ed25519 AAAA..."). Used when a file path is not convenient.
     */
    @Value("${kbo.sftp.host-key:}")
    private String hostKeyLine;

    private Session session;
    private ChannelSftp channel;

    private synchronized ChannelSftp getChannel() {
        try {
            if (channel != null && channel.isConnected()) {
                return channel;
            }
            if (session == null || !session.isConnected()) {
                JSch jsch = new JSch();
                boolean hostKeyVerified = configureHostKeyChecking(jsch);
                session = jsch.getSession(username, host, port);
                session.setPassword(password);
                Properties config = new Properties();
                if (hostKeyVerified) {
                    config.put("StrictHostKeyChecking", "yes");
                } else {
                    config.put("StrictHostKeyChecking", "no");
                    log.warn("SFTP host-key verification is DISABLED for host {} (connection is vulnerable to "
                            + "man-in-the-middle). Set kbo.sftp.known-hosts or kbo.sftp.host-key to lock it down.", host);
                }
                session.setConfig(config);
                session.connect();
            }
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            return channel;
        } catch (JSchException e) {
            throw new KboSftpException("Failed to open SFTP channel", e);
        }
    }

    /**
     * Loads known-host material into the {@link JSch} instance when configured.
     *
     * @return {@code true} if host-key verification material was loaded (so strict checking
     *         can be enabled), {@code false} if no known hosts were configured.
     */
    private boolean configureHostKeyChecking(JSch jsch) throws JSchException {
        if (knownHostsPath != null && !knownHostsPath.isBlank()) {
            jsch.setKnownHosts(knownHostsPath.trim());
            return true;
        }
        if (hostKeyLine != null && !hostKeyLine.isBlank()) {
            byte[] bytes = (hostKeyLine.trim() + "\n").getBytes(StandardCharsets.UTF_8);
            jsch.setKnownHosts(new ByteArrayInputStream(bytes));
            return true;
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.disconnect();
            } catch (Exception _) {
            }
        }
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception _) {
            }
        }
    }

    @Override
    public List<String> listFiles(String directory) {
        ChannelSftp ch = getChannel();
        try {
            Vector<ChannelSftp.LsEntry> entries = ch.ls(directory);
            List<String> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getAttrs().isDir()) {
                    result.add(entry.getFilename());
                }
            }
            return result;
        } catch (Exception e) {
            throw new KboSftpException("Failed to list files in directory: " + directory, e);
        }
    }

    @Override
    public void downloadFile(String remotePath, Path localPath) {
        ChannelSftp ch = getChannel();
        try {
            Files.createDirectories(localPath.getParent());
            try (var out = Files.newOutputStream(localPath)) {
                ch.get(remotePath, out);
            }
        } catch (IOException | RuntimeException | com.jcraft.jsch.SftpException e) {
            throw new KboSftpException("Failed to download remote file: " + remotePath, e);
        }
    }

    @Override
    public InputStream openFile(String remotePath) {
        ChannelSftp ch = getChannel();
        try {
            return ch.get(remotePath);
        } catch (com.jcraft.jsch.SftpException e) {
            throw new KboSftpException("Failed to open remote file: " + remotePath, e);
        }
    }
}
