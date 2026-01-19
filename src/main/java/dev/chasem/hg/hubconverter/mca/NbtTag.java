package dev.chasem.hg.hubconverter.mca;

public final class NbtTag {

    private final NbtType type;
    private final String name;
    private final Object value;

    public NbtTag(NbtType type, String name, Object value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    public NbtType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }
}
