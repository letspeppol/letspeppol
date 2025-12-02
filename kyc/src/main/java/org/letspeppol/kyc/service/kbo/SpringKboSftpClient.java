package org.letspeppol.kyc.service.kbo;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
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

    private ChannelSftp createChannel() {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            return channel;
        } catch (JSchException e) {
            throw new KboSftpException("Failed to open SFTP channel", e);
        }
    }

    private void disconnect(ChannelSftp channel) {
        if (channel != null) {
            try {
                Session session = channel.getSession();
                channel.disconnect();
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (JSchException ignored) {
                // ignore
            }
        }
    }

    @Override
    public List<String> listFiles(String directory) {
        ChannelSftp channel = createChannel();
        try {
            Vector<ChannelSftp.LsEntry> entries = channel.ls(directory);
            List<String> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getAttrs().isDir()) {
                    result.add(entry.getFilename());
                }
            }
            return result;
        } catch (Exception e) {
            throw new KboSftpException("Failed to list files in directory: " + directory, e);
        } finally {
            disconnect(channel);
        }
    }

    @Override
    public void downloadFile(String remotePath, Path localPath) {
        ChannelSftp channel = createChannel();
        try {
            Files.createDirectories(localPath.getParent());
            try (var out = Files.newOutputStream(localPath)) {
                channel.get(remotePath, out);
            }
        } catch (IOException | RuntimeException | com.jcraft.jsch.SftpException e) {
            throw new KboSftpException("Failed to download remote file: " + remotePath, e);
        } finally {
            disconnect(channel);
        }
    }

    @Override
    public InputStream openFile(String remotePath) {
        ChannelSftp channel = createChannel();
        try {
            return channel.get(remotePath);
        } catch (com.jcraft.jsch.SftpException e) {
            disconnect(channel);
            throw new KboSftpException("Failed to open remote file: " + remotePath, e);
        }
    }
}
