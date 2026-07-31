package dev.yskeli.musicmc.ticket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public final class UploadTicketSigner {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;
    private final Duration lifetime;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public UploadTicketSigner(PrivateKey privateKey, Duration lifetime) {
        this(privateKey, lifetime, "default");
    }

    public UploadTicketSigner(PrivateKey privateKey, Duration lifetime, String keyId) {
        this.privateKey = privateKey;
        this.lifetime = lifetime;
        if (keyId == null || !keyId.matches("[a-zA-Z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Invalid upload key ID");
        }
        this.keyId = keyId;
    }

    public static UploadTicketSigner load(Path privateKeyPath, Duration lifetime)
            throws IOException, GeneralSecurityException {
        return load(privateKeyPath, lifetime, "default");
    }

    public static UploadTicketSigner load(Path privateKeyPath, Duration lifetime, String keyId)
            throws IOException, GeneralSecurityException {
        String pem = Files.readString(privateKeyPath, StandardCharsets.US_ASCII);
        String encoded = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        return new UploadTicketSigner(key, lifetime, keyId);
    }

    public UploadTicket create(UUID playerUuid, String playerName, String songName) throws GeneralSecurityException {
        return create(playerUuid, playerName, songName, null, 0);
    }

    public UploadTicket createReplacement(
            UUID playerUuid, String playerName, String songName, String songId, int nextRevision)
            throws GeneralSecurityException {
        return create(playerUuid, playerName, songName, songId, nextRevision);
    }

    private UploadTicket create(
            UUID playerUuid, String playerName, String songName, String replacementSongId, int nextRevision)
            throws GeneralSecurityException {
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        Instant expiresAt = Instant.now().plus(lifetime);

        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("key_id", keyId);
        payload.addProperty("player_uuid", playerUuid.toString());
        payload.addProperty("player_name", playerName);
        payload.addProperty("song_name", songName);
        payload.addProperty("exp", expiresAt.getEpochSecond());
        payload.addProperty("nonce", nonce);
        if (replacementSongId != null) {
            payload.addProperty("replace_song_id", replacementSongId);
            payload.addProperty("replace_revision", nextRevision);
        }
        byte[] payloadBytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey, random);
        signature.update(payloadBytes);
        byte[] signed = signature.sign();

        String ticket = "MUS1." + BASE64_URL.encodeToString(payloadBytes) + "." + BASE64_URL.encodeToString(signed);
        return new UploadTicket(ticket, nonce, expiresAt);
    }
}
