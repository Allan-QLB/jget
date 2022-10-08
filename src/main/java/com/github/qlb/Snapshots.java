package com.github.qlb;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.netty.util.internal.StringUtil;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.iq80.leveldb.impl.Iq80DBFactory.*;
import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class Snapshots {
    private static final String DB_FILE = "jgetdb";
    static void persist(String dbFile, Snapshot snapshot) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try (DB db = factory.open(new File(dbFile), options)) {
            db.put(bytes(snapshot.id()), bytes(new Gson().toJson(snapshot)));
        }
    }

    public static void persist(Snapshot snapshot) throws IOException {
        persist(DB_FILE, snapshot);
    }

    public static TaskSnapshot load(String id) throws IOException {
        return load(DB_FILE, id);
    }

    public static void remove(String id) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try (DB db = factory.open(new File(DB_FILE), new Options())) {
            db.delete(bytes(id));
        }
    }

    public static List<TaskSnapshot> loadAllTasks() throws IOException {
        return loadAllTasks(DB_FILE);
    }

    public static List<TaskSnapshot> loadAllTasks(String dbFile) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        ArrayList<TaskSnapshot> result = new ArrayList<>();
        try (DB db = factory.open(new File(dbFile), new Options())) {
            for (Map.Entry<byte[], byte[]> next : db) {
                String json = asString(next.getValue());
                if (!StringUtil.isNullOrEmpty(json)) {
                    result.add(new Gson().fromJson(json, new TypeToken<TaskSnapshot>() {
                    }.getType()));
                }
            }
        }
        return result;
    }

    static TaskSnapshot load(String dbFile, String id) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try (DB db = factory.open(new File(dbFile), new Options())) {
            String json = asString(db.get(bytes(id)));
            if (json == null) {
                return null;
            } else {
                return new Gson().fromJson(json, new TypeToken<TaskSnapshot>() {}.getType());
            }
        }
    }

}
