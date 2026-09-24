plugins {
    id("buildsrc.convention.kotlin-jvm")
    // Kotlin の互換設定（languageVersion 2.2）と Dokka。**katachi-publish より先に**書く:
    // あちらの javadoc jar は、ここで生える dokkaGeneratePublicationJavadoc の出力を包む。
    id("buildsrc.convention.katachi-kotlin-library")
    // Maven Central へ出すのは :katachi / :katachi-konsist / :katachi-gradle-plugin の3つだけ。
    // :architecture-test には付けない。
    id("buildsrc.convention.katachi-publish")
    // 引数デコード用。buildSrc の convention plugin には入れない: あちらは :katachi-konsist と
    // 共有していて、@Serializable が要るのはこのモジュールだけ。alias の version は catalog の
    // kotlin = 2.4.10 を参照しているので、KGP と必ず一致する。
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    // Every declaration that is part of the published surface has to say so.
    explicitApi()

    // `@InternalKatachiApi` exists to stop *consumers* from depending on internals across the
    // published module boundary — it is not meant to stop katachi from depending on itself.
    // Inside `:katachi` (both main and test), writing `@file:OptIn(InternalKatachiApi::class)`
    // on every file that touches its own internals is pure ceremony, so the whole module opts in
    // here instead. The wall itself is still verified: the samples (separate Gradle builds that
    // depend on the published `katachi` artifact) keep writing `@OptIn` themselves, standing in
    // for real consumers.
    //
    // `@ExperimentalKatachiApi` is opted in for exactly the same reason, even though it says
    // something different ("this will change" rather than "do not touch"). The wall is there so
    // that a *consumer* is aware of depending on a shape that still moves; katachi is the one
    // moving it, so writing `@OptIn` inside `:katachi` would say nothing to anyone. Spelling it
    // out per file would also be worse than pointless here: the compiler reports an opt-in that
    // is already covered module-wide as an unnecessary-opt-in warning.
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
    // `implementation` ではなく `api`。`ArchitectureProcessor.argsSerializer` の型が
    // `KSerializer<Args>` なので、processor を書く利用者のコンパイルクラスパスに載る必要がある。
    //
    // これで :katachi の「実行時依存ゼロ」は成立しなくなった。README / README.ja.md / ドキュメント
    // サイトの記述を落とすのは v0.2 のステップ11 の仕事で、ここではやらない。
    api(libs.kotlinxSerializationCore)

    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}
