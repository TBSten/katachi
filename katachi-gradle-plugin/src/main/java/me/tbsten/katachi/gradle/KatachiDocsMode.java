package me.tbsten.katachi.gradle;

/**
 * Whether a documentation run writes or only compares.
 *
 * <p>Declared here rather than reused from {@code :katachi}, because the Gradle plugin depends on
 * nothing of katachi's: it hands a processor's fully qualified name to a JVM it starts and never
 * loads a katachi class itself. That independence is what keeps the plugin compilable against
 * Gradle 8.0 and free of a Kotlin metadata version. The cost is this enum, whose values have to
 * keep matching {@code DocumentationMode}'s {@code @SerialName}s -- which the jvm sample's CI run
 * is what actually checks.
 */
public enum KatachiDocsMode {

    /** Write the pages, removing any {@code *.md} this run did not produce. */
    WRITE("write"),

    /** Write nothing, and fail when the pages on disk are not what this definition produces. */
    CHECK("check");

    private final String wireName;

    KatachiDocsMode(String wireName) {
        this.wireName = wireName;
    }

    /** How this value is spelled on the command line, and in {@code DocumentationMode}. */
    String wireName() {
        return wireName;
    }
}
