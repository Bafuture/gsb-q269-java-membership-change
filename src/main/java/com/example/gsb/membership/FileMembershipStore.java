package com.example.gsb.membership;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** 基于本地文件的状态存储：先写临时文件再原子改名，避免崩溃时留下半个文件。 */
public final class FileMembershipStore implements MembershipStore {

    private final Path file;

    public FileMembershipStore(Path file) {
        this.file = file;
    }

    @Override
    public Optional<MembershipState> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (var in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            return Optional.of(new MembershipState(
                    Long.parseLong(props.getProperty("version")),
                    MembershipPhase.valueOf(props.getProperty("phase")),
                    parseMembers(props.getProperty("old")),
                    parseMembers(props.getProperty("new")),
                    Long.parseLong(props.getProperty("jointDecisions")),
                    Long.parseLong(props.getProperty("changeStartMillis")),
                    Long.parseLong(props.getProperty("changesCompleted")),
                    Long.parseLong(props.getProperty("lastChangeDurationMillis"))));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load membership state from " + file, e);
        }
    }

    @Override
    public synchronized void save(MembershipState state) {
        Properties props = new Properties();
        props.setProperty("version", Long.toString(state.version()));
        props.setProperty("phase", state.phase().name());
        props.setProperty("old", formatMembers(state.oldMembers()));
        props.setProperty("new", formatMembers(state.newMembers()));
        props.setProperty("jointDecisions", Long.toString(state.jointDecisions()));
        props.setProperty("changeStartMillis", Long.toString(state.changeStartMillis()));
        props.setProperty("changesCompleted", Long.toString(state.changesCompleted()));
        props.setProperty("lastChangeDurationMillis", Long.toString(state.lastChangeDurationMillis()));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(tmp)) {
                props.store(out, "cluster membership state");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to persist membership state to " + file, e);
        }
    }

    private static String formatMembers(Set<NodeId> members) {
        StringBuilder sb = new StringBuilder();
        for (NodeId id : members) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id.value());
        }
        return sb.toString();
    }

    private static Set<NodeId> parseMembers(String raw) {
        Set<NodeId> members = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return members;
        }
        for (String token : raw.split(",")) {
            members.add(NodeId.of(token.trim()));
        }
        return members;
    }
}
