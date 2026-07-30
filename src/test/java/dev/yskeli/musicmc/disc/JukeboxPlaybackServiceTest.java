package dev.yskeli.musicmc.disc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JukeboxPlaybackServiceTest {
    @Test
    void convertsTwentyFiveBlockRangeToMinecraftPacketVolume() {
        assertEquals(1.5625F, JukeboxPlaybackService.packetVolumeForRange(25.0F));
    }

    @Test
    void doesNotShrinkRangesBelowVanillaMinimum() {
        assertEquals(1.0F, JukeboxPlaybackService.packetVolumeForRange(8.0F));
    }
}
