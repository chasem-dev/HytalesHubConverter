package dev.chasem.hg.hubconverter.mca;

import java.util.List;
import java.util.Map;

public final class NbtUtil {

    private NbtUtil() {
    }

    public static Map<String, NbtTag> asCompound(NbtTag tag) {
        if (tag == null || tag.getType() != NbtType.COMPOUND) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, NbtTag> value = (Map<String, NbtTag>) tag.getValue();
        return value;
    }

    public static List<NbtTag> asList(NbtTag tag) {
        if (tag == null || tag.getType() != NbtType.LIST) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<NbtTag> value = (List<NbtTag>) tag.getValue();
        return value;
    }

    public static String asString(NbtTag tag) {
        if (tag == null || tag.getType() != NbtType.STRING) {
            return null;
        }
        return (String) tag.getValue();
    }

    public static Number asNumber(NbtTag tag) {
        if (tag == null) {
            return null;
        }
        return switch (tag.getType()) {
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> (Number) tag.getValue();
            default -> null;
        };
    }

    public static byte[] asByteArray(NbtTag tag) {
        if (tag == null || tag.getType() != NbtType.BYTE_ARRAY) {
            return null;
        }
        return (byte[]) tag.getValue();
    }

    public static int[] asIntArray(NbtTag tag) {
        if (tag == null || tag.getType() != NbtType.INT_ARRAY) {
            return null;
        }
        return (int[]) tag.getValue();
    }

    public static long[] asLongArray(NbtTag tag) {
        if (tag == null || tag.getType() != NbtType.LONG_ARRAY) {
            return null;
        }
        return (long[]) tag.getValue();
    }

    public static NbtTag getTag(Map<String, NbtTag> compound, String key) {
        if (compound == null) {
            return null;
        }
        return compound.get(key);
    }

    public static Map<String, NbtTag> getCompound(Map<String, NbtTag> compound, String key) {
        return asCompound(getTag(compound, key));
    }

    public static List<NbtTag> getList(Map<String, NbtTag> compound, String key) {
        return asList(getTag(compound, key));
    }

    public static String getString(Map<String, NbtTag> compound, String key) {
        return asString(getTag(compound, key));
    }

    public static Integer getInt(Map<String, NbtTag> compound, String key) {
        Number number = asNumber(getTag(compound, key));
        return number != null ? number.intValue() : null;
    }

    public static Long getLong(Map<String, NbtTag> compound, String key) {
        Number number = asNumber(getTag(compound, key));
        return number != null ? number.longValue() : null;
    }
}
