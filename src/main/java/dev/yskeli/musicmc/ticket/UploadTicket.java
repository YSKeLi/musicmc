package dev.yskeli.musicmc.ticket;

import java.time.Instant;

public record UploadTicket(String encodedTicket, String nonce, Instant expiresAt) {}
