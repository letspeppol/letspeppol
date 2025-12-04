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

import java.io.IOException;
import java.io.InputStream;
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

    private Session session;
    private ChannelSftp channel;

    private synchronized ChannelSftp getChannel() {
        try {
            if (channel != null && channel.isConnected()) {
                return channel;
            }
            if (session == null || !session.isConnected()) {
                JSch jsch = new JSch();
                session = jsch.getSession(username, host, port);
                session.setPassword(password);
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
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

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.disconnect();
            } catch (Exception ignored) {
            }
        }
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignored) {
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
