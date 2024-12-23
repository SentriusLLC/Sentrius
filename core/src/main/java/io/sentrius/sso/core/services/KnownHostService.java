package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.KnownHost;
import io.sentrius.sso.core.repository.KnownHostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class KnownHostService {

    private final KnownHostRepository knownHostRepository;
    private final File knownHostsFile;

    public KnownHostService(KnownHostRepository knownHostRepository) throws IOException {
        this.knownHostRepository = knownHostRepository;

        // Create a shared known_hosts file
        this.knownHostsFile = File.createTempFile("sentrius-", "-known-hosts");
        this.knownHostsFile.deleteOnExit();

        // Initialize the file with current known hosts
        rebuildKnownHostsFile();
    }

    @Transactional
    public void saveHostKey(String hostname, String keyType, String keyValue) {
        if (knownHostRepository.findByHostnameAndKeyType(hostname, keyType) == null) {
            KnownHost knownHost = new KnownHost();
            knownHost.setHostname(hostname);
            knownHost.setKeyType(keyType);
            knownHost.setKeyValue(keyValue);
            knownHostRepository.save(knownHost);

            // Rebuild the file after adding a new host key
            rebuildKnownHostsFile();
        }
    }

    public String getKnownHostsPath() {
        return knownHostsFile.getAbsolutePath();
    }

    private void rebuildKnownHostsFile() {
        try (FileChannel channel = FileChannel.open(knownHostsFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             FileWriter writer = new FileWriter(knownHostsFile)) {

            // Lock the file while rebuilding
            try (FileLock lock = channel.lock()) {
                List<KnownHost> knownHosts = knownHostRepository.findAll();
                for (KnownHost host : knownHosts) {
                    writer.write(host.getHostname() + " " + host.getKeyType() + " " + host.getKeyValue() + "\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rebuild known_hosts file", e);
        }
    }
}
