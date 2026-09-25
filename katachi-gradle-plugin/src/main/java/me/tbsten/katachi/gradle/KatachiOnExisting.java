package me.tbsten.katachi.gradle;

/**
 * What a template run does when a file it would write is already on disk.
 *
 * <p>Declared here rather than reused from {@code :katachi} for the reason {@link KatachiDocsMode}
 * is: the Gradle plugin loads no katachi class at all, which is what keeps it compilable against
 * Gradle 8.0 and free of a Kotlin metadata version. The cost is this enum, whose values have to
 * keep matching {@code OnExisting}'s {@code @SerialName}s -- which a sample run is what actually
 * checks.
 *
 * <p>Spelled {@code onExisting} and not {@code mode}: documentation generation already answers to
 * {@code --arg mode=}, every selected processor's arguments share one namespace in a run, and two
 * different questions must not answer to one word.
 *
 * <p><strong>All three decide about the whole set, never about one file.</strong> A template
 * produces files that belong together, so {@link #SKIP} skips the run rather than the file.
 */
public enum KatachiOnExisting {

    /** Write nothing and fail. The default: there is no undo below a generator. */
    FAIL("fail"),

    /** Write nothing and succeed, which is what makes re-running a template harmless. */
    SKIP("skip"),

    /** Replace what is there. */
    OVERWRITE("overwrite");

    private final String wireName;

    KatachiOnExisting(String wireName) {
        this.wireName = wireName;
    }

    /** How this value is spelled on the command line, and in {@code OnExisting}. */
    String wireName() {
        return wireName;
    }
}
