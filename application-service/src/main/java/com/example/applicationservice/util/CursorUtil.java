package com.example.applicationservice.util;

import com.example.applicationservice.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public class CursorUtil {

    public static String encode(Instant ts, UUID id) {
        Objects.requireNonNull(ts);
        Objects.requireNonNull(id);
        String s = ts.toString() + "|" + id.toString();
        return Base64.getUrlEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decodeOrThrow(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid cursor format");
            Instant ts = Instant.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            return new Decoded(ts, id);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new BadRequestException("Invalid cursor");
        }
    }

    public static class Decoded {
        public final Instant timestamp;
        public final UUID id;
        public Decoded(Instant timestamp, UUID id) { this.timestamp = timestamp; this.id = id; }
    }
}
