package dev.chasem.hg.hubconverter.mca;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NbtReader {

    private NbtReader() {
    }

    public static NbtTag read(InputStream inputStream) throws IOException {
        DataInputStream data = new DataInputStream(new BufferedInputStream(inputStream));
        return readNamedTag(data);
    }

    private static NbtTag readNamedTag(DataInputStream data) throws IOException {
        int typeId = data.readUnsignedByte();
        NbtType type = NbtType.fromId(typeId);
        if (type == NbtType.END) {
            return new NbtTag(type, "", null);
        }
        String name = readString(data);
        Object value = readPayload(type, data);
        return new NbtTag(type, name, value);
    }

    private static Object readPayload(NbtType type, DataInputStream data) throws IOException {
        return switch (type) {
            case END -> null;
            case BYTE -> data.readByte();
            case SHORT -> data.readShort();
            case INT -> data.readInt();
            case LONG -> data.readLong();
            case FLOAT -> data.readFloat();
            case DOUBLE -> data.readDouble();
            case BYTE_ARRAY -> readByteArray(data);
            case STRING -> readString(data);
            case LIST -> readList(data);
            case COMPOUND -> readCompound(data);
            case INT_ARRAY -> readIntArray(data);
            case LONG_ARRAY -> readLongArray(data);
        };
    }

    private static byte[] readByteArray(DataInputStream data) throws IOException {
        int length = data.readInt();
        byte[] value = new byte[length];
        data.readFully(value);
        return value;
    }

    private static int[] readIntArray(DataInputStream data) throws IOException {
        int length = data.readInt();
        int[] value = new int[length];
        for (int i = 0; i < length; i++) {
            value[i] = data.readInt();
        }
        return value;
    }

    private static long[] readLongArray(DataInputStream data) throws IOException {
        int length = data.readInt();
        long[] value = new long[length];
        for (int i = 0; i < length; i++) {
            value[i] = data.readLong();
        }
        return value;
    }

    private static List<NbtTag> readList(DataInputStream data) throws IOException {
        int elementTypeId = data.readUnsignedByte();
        NbtType elementType = NbtType.fromId(elementTypeId);
        int length = data.readInt();
        List<NbtTag> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object value = readPayload(elementType, data);
            list.add(new NbtTag(elementType, null, value));
        }
        return list;
    }

    private static Map<String, NbtTag> readCompound(DataInputStream data) throws IOException {
        Map<String, NbtTag> map = new LinkedHashMap<>();
        while (true) {
            int typeId = data.readUnsignedByte();
            NbtType type = NbtType.fromId(typeId);
            if (type == NbtType.END) {
                break;
            }
            String name = readString(data);
            Object value = readPayload(type, data);
            map.put(name, new NbtTag(type, name, value));
        }
        return map;
    }

    private static String readString(DataInputStream data) throws IOException {
        int length = data.readUnsignedShort();
        if (length <= 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
