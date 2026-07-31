package dev.yskeli.musicmc.disc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.yskeli.musicmc.model.SoundSegment;
import java.util.List;
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

    @Test
    void joinsPlaybackAtTheNextFiveSecondSegment() {
        List<SoundSegment> segments = List.of(
                new SoundSegment("musicmc:music.song.part000", 5),
                new SoundSegment("musicmc:music.song.part001", 5),
                new SoundSegment("musicmc:music.song.part002", 2));

        assertEquals(0, JukeboxPlaybackService.segmentIndexAt(segments, 4.999));
        assertEquals(1, JukeboxPlaybackService.segmentIndexAt(segments, 5.0));
        assertEquals(2, JukeboxPlaybackService.segmentIndexAt(segments, 11.9));
        assertEquals(-1, JukeboxPlaybackService.segmentIndexAt(segments, 12.0));
    }
}
