package dev.boissin.service;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.model.FileItem;

public class FileParser implements QueueConsumer<FileItem> {
    
    private static final Logger logger = LoggerFactory.getLogger(FileParser.class);
    private final String name;

    public FileParser(String name) {
        this.name = name;
    }

    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        logger.info("[{}] État de connexion changé : {}", name, newState);
    }

    @Override
    public void consumeMessage(FileItem message) throws Exception {
        logger.info("[{}] Traitement du fichier : {}", name, message);

        // simulate processing time
        Thread.sleep(10_000L);
    }
    
}
