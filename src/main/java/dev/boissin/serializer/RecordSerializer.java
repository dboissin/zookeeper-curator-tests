package dev.boissin.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.curator.framework.recipes.queue.QueueSerializer;

public class RecordSerializer<T extends Serializable> implements QueueSerializer<T> {

    @Override
    public byte[] serialize(T record) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final ObjectOutputStream out = new ObjectOutputStream(baos);
            out.writeObject(record);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RecordSerializationException("Error when serialise record", e);
        }
    }

    @Override
    public T deserialize(byte[] bytes) {
        try {
            return (T) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
        } catch (IOException|ClassNotFoundException e) {
            throw new RecordSerializationException("Error when deserialise record", e);
        }
    }

}
