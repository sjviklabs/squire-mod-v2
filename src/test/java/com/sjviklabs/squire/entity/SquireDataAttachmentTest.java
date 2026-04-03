package com.sjviklabs.squire.entity;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 unit tests for SquireDataAttachment.SquireData.
 *
 * SquireData is a pure Java record backed by a Mojang DFU Codec.
 * These tests verify:
 *   - empty() factory defaults
 *   - builder methods (withXP, withName, withAppearance, withSquireUUID)
 *   - CODEC round-trip: encode to JsonElement → decode back → identical fields
 *   - CODEC handles Optional<UUID> (present and absent)
 *
 * Run: ./gradlew test
 */
class SquireDataAttachmentTest {

    // ── empty() factory defaults ──────────────────────────────────────────────

    @Test
    void empty_totalXP_isZero() {
        assertEquals(0, SquireDataAttachment.SquireData.empty().totalXP());
    }

    @Test
    void empty_level_isZero() {
        assertEquals(0, SquireDataAttachment.SquireData.empty().level());
    }

    @Test
    void empty_customName_isSquire() {
        assertEquals("Squire", SquireDataAttachment.SquireData.empty().customName());
    }

    @Test
    void empty_slimModel_isFalse() {
        assertFalse(SquireDataAttachment.SquireData.empty().slimModel());
    }

    @Test
    void empty_squireUUID_isAbsent() {
        assertTrue(SquireDataAttachment.SquireData.empty().squireUUID().isEmpty());
    }

    @Test
    void empty_hasSquire_isFalse() {
        assertFalse(SquireDataAttachment.SquireData.empty().hasSquire());
    }

    // ── Builder methods ───────────────────────────────────────────────────────

    @Test
    void withXP_setsTotalXPAndLevel() {
        var data = SquireDataAttachment.SquireData.empty().withXP(500, 7);
        assertEquals(500, data.totalXP(), "totalXP must be 500");
        assertEquals(7,   data.level(),   "level must be 7");
    }

    @Test
    void withXP_doesNotMutateOtherFields() {
        var original = SquireDataAttachment.SquireData.empty().withName("Aldric");
        var updated  = original.withXP(1000, 10);
        assertEquals("Aldric", updated.customName(), "withXP must preserve customName");
    }

    @Test
    void withName_setsCustomName() {
        var data = SquireDataAttachment.SquireData.empty().withName("Aldric");
        assertEquals("Aldric", data.customName());
    }

    @Test
    void withAppearance_setsSlimModel() {
        var data = SquireDataAttachment.SquireData.empty().withAppearance(true);
        assertTrue(data.slimModel());
    }

    @Test
    void withSquireUUID_setsPresent() {
        UUID id   = UUID.randomUUID();
        var data  = SquireDataAttachment.SquireData.empty().withSquireUUID(Optional.of(id));
        assertTrue(data.hasSquire());
        assertEquals(id, data.squireUUID().orElseThrow());
    }

    @Test
    void clearSquireUUID_removesUUID() {
        UUID id   = UUID.randomUUID();
        var data  = SquireDataAttachment.SquireData.empty()
                .withSquireUUID(Optional.of(id))
                .clearSquireUUID();
        assertFalse(data.hasSquire());
    }

    // ── CODEC round-trip (no UUID) ────────────────────────────────────────────

    @Test
    void codecRoundTrip_noUUID() {
        var original = SquireDataAttachment.SquireData.empty()
                .withXP(1500, 12)
                .withName("Aldric")
                .withAppearance(true);

        JsonElement encoded = SquireDataAttachment.SquireData.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();

        var decoded = SquireDataAttachment.SquireData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(original.totalXP(),    decoded.totalXP());
        assertEquals(original.level(),      decoded.level());
        assertEquals(original.customName(), decoded.customName());
        assertEquals(original.slimModel(),  decoded.slimModel());
        assertTrue(decoded.squireUUID().isEmpty(), "UUID should be absent after round-trip with no UUID set");
    }

    // ── CODEC round-trip (with UUID) ──────────────────────────────────────────

    @Test
    void codecRoundTrip_withUUID() {
        UUID id = UUID.randomUUID();
        var original = SquireDataAttachment.SquireData.empty()
                .withXP(999, 5)
                .withName("Bramwell")
                .withSquireUUID(Optional.of(id));

        JsonElement encoded = SquireDataAttachment.SquireData.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();

        var decoded = SquireDataAttachment.SquireData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(original.totalXP(),    decoded.totalXP());
        assertEquals(original.level(),      decoded.level());
        assertEquals(original.customName(), decoded.customName());
        assertTrue(decoded.squireUUID().isPresent(), "UUID must survive encode/decode round-trip");
        assertEquals(id, decoded.squireUUID().get(), "UUID value must be identical after round-trip");
    }

    // ── CODEC encodes to expected JSON keys ───────────────────────────────────

    @Test
    void codecEncoding_containsExpectedFields() {
        var data = SquireDataAttachment.SquireData.empty().withName("Test");
        JsonElement encoded = SquireDataAttachment.SquireData.CODEC
                .encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow();

        assertTrue(encoded.isJsonObject(), "CODEC should produce a JSON object");
        var obj = encoded.getAsJsonObject();
        assertTrue(obj.has("totalXP"),    "Encoded JSON must contain 'totalXP'");
        assertTrue(obj.has("level"),      "Encoded JSON must contain 'level'");
        assertTrue(obj.has("customName"), "Encoded JSON must contain 'customName'");
        assertTrue(obj.has("slimModel"),  "Encoded JSON must contain 'slimModel'");
    }
}
