package dev.yskeli.musicmc.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UploadTicketSignerTest {
    @Test
    void createsVerifiableTicketWithBoundPlayerAndSong() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        UploadTicketSigner signer = new UploadTicketSigner(keyPair.getPrivate(), Duration.ofMinutes(15));
        UUID playerId = UUID.randomUUID();

        UploadTicket ticket = signer.create(playerId, "TestPlayer", "夜曲");
        String[] parts = ticket.encodedTicket().split("\\.");
        assertEquals(3, parts.length);
        assertEquals("MUS1", parts[0]);

        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(payload);
        assertTrue(verifier.verify(signatureBytes));

        JsonObject json = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, json.get("v").getAsInt());
        assertEquals(playerId.toString(), json.get("player_uuid").getAsString());
        assertEquals("TestPlayer", json.get("player_name").getAsString());
        assertEquals("夜曲", json.get("song_name").getAsString());
        assertEquals(ticket.nonce(), json.get("nonce").getAsString());
        assertTrue(ticket.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(14))));
    }
}
