package com.github.qlb;

import com.google.common.primitives.Bytes;
import com.google.gson.Gson;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.iq80.leveldb.impl.Iq80DBFactory.*;

public class Snapshots {
    private static final String DB_FILE = "jgetdb";
    static void persist(String dbFile, Snapshot snapshot) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try (DB db = factory.open(new File(dbFile), options)) {
            String clazzName = snapshot.getClass().getName();
            String json = new Gson().toJson(snapshot);
            byte[] value = Bytes.concat(new byte[]{(byte) clazzName.length()}, bytes(clazzName), bytes(json));
            db.put(bytes(snapshot.id()), value);
        }
    }

    public static void persist(Snapshot snapshot) throws IOException {
        persist(DB_FILE, snapshot);
    }

    public static Snapshot load(String id) throws IOException {
        return load(DB_FILE, id);
    }

    public static void remove(String id) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try (DB db = factory.open(new File(DB_FILE), new Options())) {
            db.delete(bytes(id));
        }
    }

    public static List<Snapshot> loadAllTasks() throws IOException {
        return loadAllTasks(DB_FILE);
    }

    public static List<Snapshot> loadAllTasks(String dbFile) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        List<Snapshot> result = new ArrayList<>();
        try (DB db = factory.open(new File(dbFile), new Options())) {
            for (Map.Entry<byte[], byte[]> next : db) {
                Snapshot snapshot = deserializeSnapshot(next.getValue());
                if (snapshot != null) {
                    result.add(snapshot);
                }
            }
        }
        return result;
    }

    static Snapshot load(String dbFile, String id) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try (DB db = factory.open(new File(dbFile), new Options())) {
            return deserializeSnapshot(db.get(bytes(id)));
        }
    }

    public static Snapshot deserializeSnapshot(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        int classLen = bytes[0];
        String className = asString(Arrays.copyOfRange(bytes, 1, classLen + 1));
        String json = asString(Arrays.copyOfRange(bytes, classLen + 1, bytes.length));
        try {
            return (Snapshot) new Gson().fromJson(json, Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
