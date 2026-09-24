package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import me.tbsten.katachi.gradle.KatachiProcessors;
import org.gradle.api.InvalidUserDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What {@code katachi { processors { } } } answers with, including what it answers without
 * being asked.
 *
 * <p>No Gradle build is started: {@link KatachiProcessors} is a plain object, and the registry
 * it produces is what the code generator reads. Whether the generated file then compiles is
 * {@code KatachiPluginFunctionalTest}'s question, and whether the registered class actually
 * exists is a sample run's.
 */
class KatachiProcessorsTest {

    /** Where the default documentation processor lives, as the generated source has to spell it. */
    private static final String GENERATE_DOCUMENTATION = "me.tbsten.katachi.docs.GenerateDocumentation";

    @Test
    @DisplayName("何も register しなくても docs が引ける")
    void docsIsRegisteredByDefault() {
        Map<String, String> registrations = new KatachiProcessors().getRegistrations();

        assertEquals(GENERATE_DOCUMENTATION, registrations.get("docs"));
    }

    @Test
    @DisplayName("既定で登録されるのは docs だけ -- layout も konsist も入らない")
    void docsIsTheOnlyDefault() {
        Map<String, String> registrations = new KatachiProcessors().getRegistrations();

        // `konsist` would point into :katachi-konsist, which a module that does not depend on
        // it cannot compile a `::class.java` literal against. `layout` is left out with it so
        // that the rule stays "only what :katachi itself carries".
        assertEquals(
                Map.of("docs", GENERATE_DOCUMENTATION),
                registrations,
                "only processors living in :katachi itself may be defaulted");
    }

    @Test
    @DisplayName("同じキーを register すると利用者の登録が勝つ")
    void aUserRegistrationOverridesADefault() {
        KatachiProcessors processors = new KatachiProcessors();
        processors.register("docs", "com.example.processors.OurOwnDocs");

        assertEquals("com.example.processors.OurOwnDocs", processors.getRegistrations().get("docs"));
    }

    @Test
    @DisplayName("既定のキーを register しても「登録済み」で落ちない")
    void registeringADefaultKeyIsNotADuplicate() {
        KatachiProcessors processors = new KatachiProcessors();

        // Without this, swapping in a documentation processor of one's own would be impossible
        // without giving it a second name.
        processors.register("docs", "com.example.processors.OurOwnDocs");

        assertEquals(1, processors.getRegistrations().size());
    }

    @Test
    @DisplayName("同じキーを2回 register すると、これまでどおり落ちる")
    void registeringTheSameKeyTwiceStillFails() {
        KatachiProcessors processors = new KatachiProcessors();
        processors.register("docs", "com.example.processors.OurOwnDocs");

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.register("docs", "com.example.processors.YetAnother"));

        assertTrue(
                failure.getMessage().contains("com.example.processors.OurOwnDocs"),
                "expected the message to name what the key already points at: " + failure.getMessage());
    }

    @Test
    @DisplayName("利用者の登録は既定に足される")
    void userRegistrationsAreAddedToTheDefaults() {
        KatachiProcessors processors = new KatachiProcessors();
        processors.register("layout", "me.tbsten.katachi.check.LayoutCheck");

        Map<String, String> registrations = processors.getRegistrations();

        assertEquals(GENERATE_DOCUMENTATION, registrations.get("docs"));
        assertEquals("me.tbsten.katachi.check.LayoutCheck", registrations.get("layout"));
    }

    @Test
    @DisplayName("返る Map を書き換えても、次に引いたときに残らない")
    void theReturnedMapIsACopy() {
        KatachiProcessors processors = new KatachiProcessors();

        processors.getRegistrations().put("docs", "com.example.Sneaky");

        assertEquals(GENERATE_DOCUMENTATION, processors.getRegistrations().get("docs"));
    }
}
