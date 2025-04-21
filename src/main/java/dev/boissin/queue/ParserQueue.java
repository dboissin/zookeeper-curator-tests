package dev.boissin.queue;

import java.util.List;

import org.apache.curator.framework.recipes.queue.MultiItem;

import dev.boissin.model.FileItem;
import dev.boissin.serializer.RecordSerializer;
import dev.boissin.service.FileParser;

public class ParserQueue extends AbstractDistributedQueue<FileItem> {

    public static final String PARSER_QUEUE_PATH = "/queues/parser";
    public static final String PARSER_LOCK_PATH = "/locks/parser";

    public ParserQueue(boolean worker, String name) {
        super(PARSER_QUEUE_PATH, PARSER_LOCK_PATH, new RecordSerializer<>(), (worker ? new FileParser(name): null));
    }

    public void sendFileItem(FileItem fileItem) throws Exception {
        queue.put(fileItem);
    }

    public void sendFileItems(MultiItem<FileItem> fileItems) throws Exception {
        queue.putMulti(fileItems);
    }

}
