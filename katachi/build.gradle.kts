plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.katachi-kotlin-library")
    id("buildsrc.convention.katachi-publish")
    alias(libs.plugins.kotlinPluginSerialization)
    // 合成フィクスチャ（src/testFixtures）を :katachi の test と、後で作る :benchmark の両方から使うため。
    // 公開はしない。下の components / dokka の設定で成果物から外している。
    `java-test-fixtures`
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("me.tbsten.katachi.InternalKatachiApi")
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
        // `AbstractDecoder` は @ExperimentalSerializationApi。`StringMapDecoder` はそれを継承する
        // ので、ファイルごとの @OptIn ではなくモジュール全体で opt-in する（katachi 自身の2つの
        // マーカーと同じ扱い）。
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    api(libs.kotlinxSerializationCore)

    // `@Language("markdown")` を DSL のプロパティに付けるためだけ。実行時には要らない注釈なので
    // compileOnly（利用者の依存グラフには出ない）。
    compileOnly(libs.jetbrainsAnnotations)

    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}

// kotest はタグの指定をテスト JVM のシステムプロパティで読む。`./gradlew :katachi:test -Dkotest.tags.exclude=Perf`
// の -D は Gradle 側の JVM にしか届かないので、テスト JVM へ渡し直す（時間を測る Perf タグのスペックを手元で外すため）。
tasks.test {
    listOf("kotest.tags", "kotest.tags.include", "kotest.tags.exclude").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}

// java-test-fixtures は既定で `java` component に testFixtures の variant を足し、Maven Central に
// `-test-fixtures.jar`（と sources jar）と Gradle Module Metadata の variant が載ってしまう。
// 合成フィクスチャはこのリポジトリの test と :benchmark のためだけのものなので、公開物からは外す。
// sources の variant は maven-publish の plugin が sources jar を有効にしたときに後から生えるので、
// 名前で待ち受けて外す。
val testFixturesVariants = setOf(
    "testFixturesApiElements",
    "testFixturesRuntimeElements",
    "testFixturesSourcesElements",
)
afterEvaluate {
    components.named("java", AdhocComponentWithVariants::class.java) {
        configurations.matching { it.name in testFixturesVariants }.forEach { variant ->
            withVariantsFromConfiguration(variant) { skip() }
        }
    }
}

// API リファレンス（HTML / javadoc jar）にも testFixtures を出さない。
dokka {
    dokkaSourceSets.matching { it.name == "testFixtures" }.configureEach {
        suppress.set(true)
    }
}
