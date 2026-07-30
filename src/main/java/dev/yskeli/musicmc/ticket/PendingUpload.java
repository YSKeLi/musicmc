package dev.yskeli.musicmc.ticket;

import java.time.Instant;
import java.util.UUID;

public record PendingUpload(String nonce, UUID playerUuid, String songName, Instant expiresAt) {}
