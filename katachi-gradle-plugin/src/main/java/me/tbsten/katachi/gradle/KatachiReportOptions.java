package me.tbsten.katachi.gradle;

/**
 * What the architecture test reports besides failing, written in {@code katachi { report { } }}.
 *
 * <p>With {@link #setSarif(boolean) sarif} on, every {@code Test} task of the module writes its
 * violations as a SARIF 2.1.0 file that GitHub code scanning or reviewdog can show as
 * annotations on a pull request ({@link KatachiReportArguments}).
 *
 * <h2>Example 1: write the SARIF file</h2>
 *
 * <pre>{@code
 * katachi {
 *     report {
 *         sarif = true
 *     }
 * }
 * }</pre>
 */
public class KatachiReportOptions {

    private boolean sarif;

    /**
     * Whether the test JVM is asked to write a SARIF file.
     *
     * <p>Named {@code getSarif} rather than {@code isSarif}: Kotlin's {@code sarif = true} in a
     * build script reads a Java boolean getter as {@code isSarif}, not {@code sarif}.
     *
     * @return {@code true} when {@code sarif = true} was written; {@code false} by default.
     */
    public boolean getSarif() {
        return sarif;
    }

    /**
     * Sets whether the test JVM writes a SARIF file, to {@code build/reports/katachi/katachi.sarif}
     * for the {@code test} task and {@code build/reports/katachi/<task>/katachi.sarif} for any
     * other {@code Test} task.
     *
     * @param sarif {@code true} to write it.
     */
    public void setSarif(boolean sarif) {
        this.sarif = sarif;
    }
}
