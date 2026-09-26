package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import me.tbsten.katachi.gradle.KatachiOnExisting;
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

    /** Where the default template processor lives, as the generated source has to spell it. */
    private static final String GENERATE_CODE_FROM_TEMPLATE =
            "me.tbsten.katachi.template.GenerateCodeFromTemplate";

    /** Where the default template describer lives, as the generated source has to spell it. */
    private static final String DESCRIBE_TEMPLATES = "me.tbsten.katachi.template.DescribeTemplates";

    @Test
    @DisplayName("何も register しなくても docs が引ける")
    void docsIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(new KatachiProcessors());

        assertEquals(GENERATE_DOCUMENTATION, registrations.get("docs"));
    }

    @Test
    @DisplayName("何も register しなくても template が引ける")
    void templateIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(new KatachiProcessors());

        assertEquals(GENERATE_CODE_FROM_TEMPLATE, registrations.get("template"));
    }

    @Test
    @DisplayName("何も register しなくても templates が引ける")
    void templatesIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(new KatachiProcessors());

        assertEquals(DESCRIBE_TEMPLATES, registrations.get("templates"));
    }

    @Test
    @DisplayName("既定で登録されるのは docs と template と templates だけ -- layout も konsist も入らない")
    void docsAndTemplateAreTheOnlyDefaults() {
        Map<String, String> registrations = registrations(new KatachiProcessors());

        // `konsist` would point into :katachi-konsist, which a module that does not depend on
        // it cannot compile a `::class.java` literal against. `layout` is left out with it so
        // that the rule stays "only what :katachi itself carries".
        Map<String, String> expected = new HashMap<>();
        expected.put("docs", GENERATE_DOCUMENTATION);
        expected.put("template", GENERATE_CODE_FROM_TEMPLATE);
        expected.put("templates", DESCRIBE_TEMPLATES);
        assertEquals(
                expected,
                registrations,
                "only processors living in :katachi itself may be defaulted");
    }

    @Test
    @DisplayName("同じキーを register すると利用者の登録が勝つ")
    void aUserRegistrationOverridesADefault() {
        KatachiProcessors processors = new KatachiProcessors();
        processors.register("docs", "com.example.processors.OurOwnDocs");

        assertEquals("com.example.processors.OurOwnDocs", registrations(processors).get("docs"));
    }

    @Test
    @DisplayName("既定のキーを register しても「登録済み」で落ちない")
    void registeringADefaultKeyIsNotADuplicate() {
        KatachiProcessors processors = new KatachiProcessors();

        // Without this, swapping in a documentation processor of one's own would be impossible
        // without giving it a second name.
        processors.register("docs", "com.example.processors.OurOwnDocs");

        // Still the three defaults, with `docs` now pointing at the user's own class.
        assertEquals(3, registrations(processors).size());
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

        Map<String, String> registrations = registrations(processors);

        assertEquals(GENERATE_DOCUMENTATION, registrations.get("docs"));
        assertEquals("me.tbsten.katachi.check.LayoutCheck", registrations.get("layout"));
    }

    @Test
    @DisplayName("template { } と args(\"template\") { } の併用は二重設定として落ちる")
    void aTypedBlockStillCollidesWithTheStringForm() {
        KatachiProcessors processors = new KatachiProcessors();
        processors.template(template -> template.setRoleName("UseCase"));
        processors.args("template", args -> args.arg("roleName", "UseCase"));

        InvalidUserDataException failure =
                assertThrows(InvalidUserDataException.class, () -> configuredArgs(processors));

        assertTrue(
                failure.getMessage().contains("template { }"),
                "expected the message to name the typed block: " + failure.getMessage());
    }

    @Test
    @DisplayName("template { onExisting = SKIP } は --arg onExisting=skip になる")
    void onExistingTravelsAsItsWireName() {
        KatachiProcessors processors = new KatachiProcessors();
        processors.template(template -> template.setOnExisting(KatachiOnExisting.SKIP));

        assertEquals("skip", configuredArgs(processors).get("template").get("onExisting"));
    }

    /**
     * Calls the package-private {@code KatachiProcessors.getConfiguredArgs()}.
     *
     * <p>Reflection for the reason {@code KatachiEntryPointSourceTest} uses it: this test sits in
     * {@code me.tbsten.katachi.test.gradle} like every other test of this module, so it sees the
     * plugin under the same visibility a user's build does. Opening the method up for the test
     * would change what the Gradle DSL exposes.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> configuredArgs(KatachiProcessors processors) {
        return (Map<String, Map<String, String>>) invoke(processors, "getConfiguredArgs");
    }

    /**
     * Calls the package-private {@code KatachiProcessors.getRegistrations()}, for the same reason
     * {@link #configuredArgs} does.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> registrations(KatachiProcessors processors) {
        return (Map<String, String>) invoke(processors, "getRegistrations");
    }

    private static Object invoke(KatachiProcessors processors, String methodName) {
        try {
            Method method = KatachiProcessors.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(processors);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("返る Map を書き換えても、次に引いたときに残らない")
    void theReturnedMapIsACopy() {
        KatachiProcessors processors = new KatachiProcessors();

        registrations(processors).put("docs", "com.example.Sneaky");

        assertEquals(GENERATE_DOCUMENTATION, registrations(processors).get("docs"));
    }
}
