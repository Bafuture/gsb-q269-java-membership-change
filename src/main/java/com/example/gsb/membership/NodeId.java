package com.example.gsb.membership;

import java.util.Objects;

/** 集群节点的唯一标识。 */
public record NodeId(String value) {

    public NodeId {
        Objects.requireNonNull(value, "node id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
        if (value.chars().anyMatch(c -> c == ',' || c == '\n' || c == '\r' || c == '=')) {
            throw new IllegalArgumentException("node id must not contain ',', '=' or line breaks: " + value);
        }
    }

    public static NodeId of(String value) {
        return new NodeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
