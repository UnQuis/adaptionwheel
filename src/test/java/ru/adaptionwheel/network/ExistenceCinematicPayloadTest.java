package ru.adaptionwheel.network;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A long or multibyte boss name must not cost the player the reward.
 *
 * <p>{@code FriendlyByteBuf.writeUtf(String, int)} throws rather than truncating, on two separate
 * counts: UTF-16 length, and encoded UTF-8 bytes against {@code ByteBufUtil.utf8MaxBytes}. Clamping
 * to code points satisfies neither -- an astral character is two units and up to four bytes -- so
 * the payload clamps against both limits and these are the inputs that used to throw an
 * {@code EncoderException} instead of sending.
 *
 * <p>A gametest on the 1.21.1 branch; 26.3 removed that harness, so the assertions moved here
 * unchanged. See {@code ExistenceCinematicTimingTest} for the same note.
 */
class ExistenceCinematicPayloadTest {

    @Test
    void multibyteBossNamesStillEncode() {
        String[] names = {
                "Warden",
                "\uD83D\uDE00".repeat(200),                          // 200 emoji: 400 units, 800 bytes
                "\uD83D\uDE00".repeat(64),                           // exactly at the unit limit
                "\u0416\u043B\u0435\u0437\u043D\u044B\u0439".repeat(40), // Cyrillic, 2 bytes each
                "\u30C9\u30E9\u30DE".repeat(60),                    // CJK, 3 bytes each
                "x".repeat(500),                                     // plain but far too long
                "",
        };

        for (String name : names) {
            String note = "name of " + name.codePointCount(0, name.length()) + " code points";
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ExistenceCinematicPayload.STREAM_CODEC.encode(buf, new ExistenceCinematicPayload(7, name));
                ExistenceCinematicPayload back = ExistenceCinematicPayload.STREAM_CODEC.decode(buf);

                assertEquals(7, back.bossEntityId(), "the entity id must survive alongside the clamped name, " + note);
                assertTrue(back.bossName().length() <= 128,
                        "a decoded name of " + back.bossName().length()
                                + " units would not have been readable back, " + note);
                assertTrue(ByteBufUtil.utf8Bytes(back.bossName()) <= ByteBufUtil.utf8MaxBytes(128),
                        "the decoded name still exceeds the byte budget, " + note);
            } catch (RuntimeException e) {
                // A throw here is the failure this test exists for: it would have cost the reward.
                throw new AssertionError(note + " threw instead of being clamped", e);
            } finally {
                buf.release();
            }
        }
    }

    /**
     * The clamped prefix must still be the name, not a mangled half of a surrogate pair -- a name cut
     * mid-emoji would not just look wrong, it would not survive the second write.
     */
    @Test
    void truncationCutsOnACodePointBoundary() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ExistenceCinematicPayload.STREAM_CODEC.encode(buf,
                    new ExistenceCinematicPayload(1, "\uD83D\uDE00".repeat(200)));
            ExistenceCinematicPayload back = ExistenceCinematicPayload.STREAM_CODEC.decode(buf);
            assertTrue(back.bossName().codePoints().allMatch(cp -> cp == 0x1F600),
                    "truncation must cut on a code point boundary, not split an emoji");
            assertEquals(64, back.bossName().codePointCount(0, back.bossName().length()),
                    "the emoji floor is 64 by the 128-unit limit");
        } finally {
            buf.release();
        }
    }

    /** The clamp walks code points; it must stop before one that would cross either limit. */
    @Test
    void shortNamesAreSentUnchanged() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ExistenceCinematicPayload.STREAM_CODEC.encode(buf, new ExistenceCinematicPayload(-1, "Warden"));
            ExistenceCinematicPayload back = ExistenceCinematicPayload.STREAM_CODEC.decode(buf);
            assertEquals("Warden", back.bossName(), "a name inside both limits must not be touched");
            assertEquals(-1, back.bossEntityId(), "-1 is how a despawned boss is reported");
        } finally {
            buf.release();
        }
    }
}