package dev.boissin.model;

import java.util.Iterator;
import java.util.List;

import org.apache.curator.framework.recipes.queue.MultiItem;

public class FileItems implements MultiItem<FileItem> {

    private final Iterator<FileItem> iterator;
    
    public FileItems(List<FileItem> fileItems) {
        this.iterator = fileItems.iterator();
    }

    @Override
    public FileItem nextItem() throws Exception {
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
    
}
