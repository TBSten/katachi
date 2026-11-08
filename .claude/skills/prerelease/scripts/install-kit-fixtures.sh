# check-install-kit.sh が読み込む（単独では実行しない）。
# インストールキット（katachi コマンドの agent init / scaffold / agent test）を試す、合成の Gradle プロジェクト（fixture）の定義。
#
# docs/AGENTS.md の「public/install/」の節の3種（agp / noroot / rootjvm）と、
# これまでに不具合が出た形を1つずつ。足すときは FIXTURES・fx_<名前>・fx_desc_<名前>・fx_anchor_<名前> を揃える。
#
# 呼び出し側が決めておく変数: REPO（リポジトリの直下）、K（Kotlin の版）、KOTEST（Kotest の版）、AGP（AGP の版）
#
# fx_toolchain_<名前>（任意）は「scaffold が architecture-test/build.gradle.kts に書くべき jvmToolchain の N」。
# none は「jvmToolchain の行を書かない」。定義した fixture だけ、生成物と突き合わせる。
#
# fx_anchor_<名前> は「scaffold が足した include の直前に来るべき行」（前後の空白を除いた中身）。
# END は「include が無いので末尾に足される」。
#
# fx_module_dir_<名前>（任意）は init に渡す --module-dir（検査用モジュールの置き場所。例: static-analysis/katachi）。
# fx_scaffold_args_<名前>（任意）は scaffold に足す引数（例: --test-framework kotest）。
# fx_expect_<名前>（任意。$1 = fixture のディレクトリ、$2 = katachi コマンド）は scaffold の生成物の検査。
# NG の理由を 1 行ずつ標準出力に出す（何も出さなければ OK）。

FIXTURES="agp noroot rootjvm subonly groovyinc multiinc withid buildscript catalogalias jdkonly tc21 nestedmod catalogwrite kotestdetect tcval buildlogic"

fx_wrapper() {
	mkdir -p "$1/gradle/wrapper"
	cp "${REPO}/gradlew" "$1/"
	cp "${REPO}/gradle/wrapper/gradle-wrapper.jar" "${REPO}/gradle/wrapper/gradle-wrapper.properties" "$1/gradle/wrapper/"
	printf '.local/\nbuild/\n.gradle/\n.kotlin/\nlocal.properties\n' >"$1/.gitignore"
}

# 利用者のリポジトリと同じく、git 管理下で作業ツリーがきれいな状態にする（katachi は git の追跡から対象を拾う）。
fx_git_init() {
	(cd "$1" && git init -q &&
		git add -A &&
		git -c user.name=katachi-prerelease -c user.email=prerelease@example.invalid -c commit.gpgsign=false \
			commit -qm init)
}

# ---- 1. AGP + version catalog ----------------------------------------------------------------
fx_desc_agp() { echo "AGP + version catalog"; }
fx_anchor_agp() { echo 'include(":app")'; }
fx_agp() {
	d="$1"
	mkdir -p "$d/app/src/main"
	cat >"$d/gradle/libs.versions.toml" <<EOF
[versions]
agp = "${AGP}"
kotlin = "${K}"

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
EOF
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "fx-agp"
include(":app")
EOF
	cat >"$d/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}
EOF
	cat >"$d/app/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.androidApplication)
}
android {
    namespace = "com.example.fx"
    compileSdk = 36
}
EOF
	printf '<manifest />\n' >"$d/app/src/main/AndroidManifest.xml"
	printf 'android.useAndroidX=true\n' >"$d/gradle.properties"
	# SDK の場所はリポジトリの local.properties か ANDROID_HOME から（git には入れない）。
	if [ -f "${REPO}/local.properties" ]; then
		grep '^sdk.dir=' "${REPO}/local.properties" >"$d/local.properties"
	elif [ -n "${ANDROID_HOME:-}" ]; then
		printf 'sdk.dir=%s\n' "${ANDROID_HOME}" >"$d/local.properties"
	fi
}

# ---- 2. ルートの build ファイル無し（サブプロジェクトは Kotlin を持たない） ------------------
fx_desc_noroot() { echo "ルートの build ファイル無し（Kotlin は gradle.properties の版だけ）"; }
fx_anchor_noroot() { echo 'include(":lib")'; }
fx_noroot() {
	d="$1"
	mkdir -p "$d/lib/src/main/java/fx"
	cat >"$d/settings.gradle.kts" <<'EOF'
rootProject.name = "fx-noroot"
include(":lib")
EOF
	printf 'plugins {\n    `java-library`\n}\n' >"$d/lib/build.gradle.kts"
	printf 'package fx;\npublic class A {}\n' >"$d/lib/src/main/java/fx/A.java"
	printf 'kotlin.version=%s\n' "${K}" >"$d/gradle.properties"
}

# ---- 3. Kotlin JVM がすでにルートに居る（include が1つも無い） --------------------------------
fx_desc_rootjvm() { echo "Kotlin JVM が既にルートに居る（include 無し）"; }
fx_anchor_rootjvm() { echo 'END'; }
fx_rootjvm() {
	d="$1"
	mkdir -p "$d/src/main/kotlin/fx"
	printf 'rootProject.name = "fx-rootjvm"\n' >"$d/settings.gradle.kts"
	cat >"$d/build.gradle.kts" <<EOF
plugins {
    kotlin("jvm") version "${K}"
}
repositories { mavenCentral() }
EOF
	printf 'package fx\nclass A\n' >"$d/src/main/kotlin/fx/A.kt"
}

# ---- 4. ルート無し、サブプロジェクトだけが版付きで multiplatform を宣言（kdoctor の形） -------
fx_desc_subonly() { echo "サブプロジェクトだけが版付きで Kotlin を宣言"; }
fx_anchor_subonly() { echo 'include(":shared")'; }
fx_subonly() {
	d="$1"
	mkdir -p "$d/shared/src/commonMain/kotlin/fx"
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-subonly"
include(":shared")
EOF
	cat >"$d/shared/build.gradle.kts" <<EOF
plugins {
    kotlin("multiplatform") version "${K}"
}
kotlin {
    jvm()
}
EOF
	printf 'package fx\nclass A\n' >"$d/shared/src/commonMain/kotlin/fx/A.kt"
}

# ---- 5. Groovy の複数行 include --------------------------------------------------------------
fx_desc_groovyinc() { echo "Groovy DSL の複数行 include"; }
fx_anchor_groovyinc() { echo "':b'"; }
fx_groovyinc() {
	d="$1"
	mkdir -p "$d/a" "$d/b"
	cat >"$d/settings.gradle" <<'EOF'
rootProject.name = 'fx-groovyinc'
include ':a',
    ':b'
EOF
	printf "plugins {\n    id 'java-library'\n}\n" >"$d/a/build.gradle"
	printf "plugins {\n    id 'java-library'\n}\n" >"$d/b/build.gradle"
	printf 'kotlin.version=%s\n' "${K}" >"$d/gradle.properties"
}

# ---- 6. Kotlin DSL の複数行 include(（後ろに別のブロックが続く） -----------------------------
fx_desc_multiinc() { echo "Kotlin DSL の複数行 include(（後ろにブロック）"; }
fx_anchor_multiinc() { echo ')'; }
fx_multiinc() {
	d="$1"
	mkdir -p "$d/a/src/main/kotlin/fx" "$d/b/src/main/kotlin/fx"
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "fx-multiinc"
include(
    ":a",
    ":b",
)
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
EOF
	cat >"$d/build.gradle.kts" <<EOF
plugins {
    kotlin("jvm") version "${K}" apply false
}
EOF
	for m in a b; do
		printf 'plugins {\n    kotlin("jvm")\n}\n' >"$d/$m/build.gradle.kts"
		printf 'package fx\nclass C%s\n' "$m" >"$d/$m/src/main/kotlin/fx/C$m.kt"
	done
}

# ---- 7. ルートは plugins.withId("org.jetbrains.kotlin.jvm") の文字列だけ、app が版付き ------
fx_desc_withid() { echo "ルートに plugins.withId(\"org.jetbrains.kotlin.jvm\") の文字列だけ"; }
fx_anchor_withid() { echo 'include(":app")'; }
fx_withid() {
	d="$1"
	mkdir -p "$d/app/src/main/kotlin/fx"
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-withid"
include(":app")
EOF
	cat >"$d/build.gradle.kts" <<'EOF'
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        println("kotlin")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { }
}
EOF
	printf 'plugins {\n    kotlin("jvm") version "%s"\n}\n' "${K}" >"$d/app/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/app/src/main/kotlin/fx/A.kt"
}

# ---- 8. buildscript の classpath に kotlin-gradle-plugin、サブプロジェクトは apply(plugin = ...) --
fx_desc_buildscript() { echo "buildscript の classpath に kotlin-gradle-plugin"; }
fx_anchor_buildscript() { echo 'include(":app")'; }
fx_buildscript() {
	d="$1"
	mkdir -p "$d/app/src/main/kotlin/fx"
	cat >"$d/settings.gradle.kts" <<'EOF'
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-buildscript"
include(":app")
EOF
	cat >"$d/build.gradle.kts" <<EOF
buildscript {
    repositories {
        // https://plugins.gradle.org/m2/ も見る
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${K}")
    }
}
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
}
EOF
	: >"$d/app/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/app/src/main/kotlin/fx/A.kt"
}

# ---- 9. catalog の別名の Kotlin（名前に kotlin を含まない別名。composables-ui の形） ------------
fx_desc_catalogalias() { echo "catalog の別名の Kotlin（jvm = { id = \"org.jetbrains.kotlin.jvm\" }）"; }
fx_anchor_catalogalias() { echo 'include(":app")'; }
fx_catalogalias() {
	d="$1"
	mkdir -p "$d/app/src/main/kotlin/fx"
	cat >"$d/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"

[plugins]
jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
EOF
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-catalogalias"
include(":app")
EOF
	printf 'plugins {\n    alias(libs.plugins.jvm) apply false\n}\n' >"$d/build.gradle.kts"
	printf 'plugins {\n    alias(libs.plugins.jvm)\n}\n' >"$d/app/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/app/src/main/kotlin/fx/A.kt"
}

# ---- 10, 11. JDK 21 だけの環境（toolchain の自動検出を切る。foojay も無い） -----------------------
# 手元に JDK 17 があっても再現できるよう、gradle.properties で org.gradle.java.installations.auto-detect=false
# にする。こうすると Gradle が知っている JDK は「Gradle を動かしている JDK」だけになり、jvmToolchain(17) は
# "Cannot find a Java installation ... languageVersion=17" で落ちる（prerelease 9-2 の警告の再現）。
# Gradle を動かす JDK が 21 であることが前提（tc21 の期待は 21 を書いてある）。
fx_only_running_jdk() {
	printf 'org.gradle.java.installations.auto-detect=false\n' >>"$1/gradle.properties"
}

fx_desc_jdkonly() { echo "JDK 21 だけ・foojay 無し・toolchain の設定無し（jvmToolchain を書かない）"; }
fx_anchor_jdkonly() { echo 'END'; }
fx_toolchain_jdkonly() { echo none; }
fx_jdkonly() {
	d="$1"
	mkdir -p "$d/src/main/kotlin/fx"
	printf 'rootProject.name = "fx-jdkonly"\n' >"$d/settings.gradle.kts"
	cat >"$d/build.gradle.kts" <<EOF
plugins {
    kotlin("jvm") version "${K}"
}
repositories { mavenCentral() }
EOF
	printf 'package fx\nclass A\n' >"$d/src/main/kotlin/fx/A.kt"
	fx_only_running_jdk "$d"
}

fx_desc_tc21() { echo "JDK 21 だけ・foojay 無し・ほかのモジュールに jvmToolchain(21)"; }
fx_anchor_tc21() { echo 'include(":lib")'; }
fx_toolchain_tc21() { echo 21; }
fx_tc21() {
	d="$1"
	mkdir -p "$d/lib/src/main/kotlin/fx"
	cat >"$d/settings.gradle.kts" <<'EOF'
rootProject.name = "fx-tc21"
include(":lib")
EOF
	cat >"$d/build.gradle.kts" <<EOF
plugins {
    kotlin("jvm") version "${K}" apply false
}
EOF
	cat >"$d/lib/build.gradle.kts" <<'EOF'
plugins {
    kotlin("jvm")
}
repositories { mavenCentral() }
kotlin {
    jvmToolchain(21)
}
EOF
	printf 'package fx\nclass A\n' >"$d/lib/src/main/kotlin/fx/A.kt"
	fx_only_running_jdk "$d"
}

# ---- 12. --module-dir の入れ子（static-analysis/katachi。include とタスクのパスが入れ子になる） ----
fx_desc_nestedmod() { echo "--module-dir static-analysis/katachi（include とタスクのパスが入れ子）"; }
fx_anchor_nestedmod() { echo 'include(":lib")'; }
fx_module_dir_nestedmod() { echo "static-analysis/katachi"; }
fx_nestedmod() {
	d="$1"
	mkdir -p "$d/src/main/kotlin/fx" "$d/lib/src/main/java/fx"
	printf 'rootProject.name = "fx-nestedmod"\ninclude(":lib")\n' >"$d/settings.gradle.kts"
	cat >"$d/build.gradle.kts" <<EOF
plugins {
    kotlin("jvm") version "${K}"
}
repositories { mavenCentral() }
EOF
	printf 'package fx\nclass A\n' >"$d/src/main/kotlin/fx/A.kt"
	printf 'plugins {\n    `java-library`\n}\n' >"$d/lib/build.gradle.kts"
	printf 'package fx;\npublic class B {}\n' >"$d/lib/src/main/java/fx/B.java"
}
fx_expect_nestedmod() {
	[ -f "$1/static-analysis/katachi/build.gradle.kts" ] || echo "static-analysis/katachi/build.gradle.kts が無い"
	[ -d "$1/architecture-test" ] && echo "architecture-test/ が作られた（--module-dir が効いていない）"
	[ -f "$1/static-analysis/build.gradle.kts" ] && echo "親の static-analysis/ に build ファイルを書いた（親には何も書かない決まり）"
	return 0
}

# ---- 13. version catalog への追記（kotlin だけの catalog + Kotlin JVM がルート。konsist あり） ----
# toml に katachi / katachiKonsist / junit-* が足され、build.gradle.kts が alias(libs.plugins.katachi) と
# libs.katachi / libs.katachiKonsist で参照する。:test が構成に通れば「libs.katachi がサブグループのアクセサで
# 落ちない」ことの判定。2 回目の scaffold --force で重複しない。
fx_desc_catalogwrite() { echo "version catalog に katachi / katachiKonsist / junit を追記（konsist あり、--force の 2 回目で重複しない）"; }
fx_anchor_catalogwrite() { echo 'END'; }
fx_catalogwrite() {
	d="$1"
	mkdir -p "$d/src/main/kotlin/fx"
	cat >"$d/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"

[libraries]
guava = { module = "com.google.guava:guava", version = "33.0.0-jre" }

[plugins]
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
EOF
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-catalogwrite"
EOF
	printf 'plugins {\n    alias(libs.plugins.kotlinJvm)\n}\n' >"$d/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/src/main/kotlin/fx/A.kt"
}
fx_expect_catalogwrite() {
	_t="$1/gradle/libs.versions.toml"
	_b="$1/architecture-test/build.gradle.kts"
	grep -q '^katachi = "' "${_t}" || echo "toml の [versions] に katachi が無い"
	grep -q '^katachi = { module = "me.tbsten.katachi:katachi", version.ref = "katachi" }' "${_t}" || echo "toml の [libraries] に katachi が無い"
	grep -q '^katachiKonsist = { module = "me.tbsten.katachi:katachi-konsist"' "${_t}" || echo "toml の [libraries] に katachiKonsist が無い"
	grep -q '^junit-bom = ' "${_t}" || echo "toml の [libraries] に junit-bom が無い"
	grep -q '^katachi = { id = "me.tbsten.katachi", version.ref = "katachi" }' "${_t}" || echo "toml の [plugins] に katachi が無い"
	grep -q 'alias(libs.plugins.katachi)' "${_b}" || echo "build.gradle.kts に alias(libs.plugins.katachi) が無い"
	grep -q 'testImplementation(libs.katachi)' "${_b}" || echo "build.gradle.kts に libs.katachi が無い"
	grep -q 'testImplementation(libs.katachiKonsist)' "${_b}" || echo "build.gradle.kts に libs.katachiKonsist が無い"
	grep -q 'me.tbsten.katachi:' "${_b}" && echo "build.gradle.kts に座標の直書きが残っている"
	# 2 回目の --force で重複しない
	(cd "$1" && "$2" scaffold --package com.example.fx --force) >"$1/scaffold-force.log" 2>&1 || echo "scaffold --force の 2 回目が失敗（$1/scaffold-force.log）"
	_n=`grep -c '^katachi = "' "${_t}"`
	[ "${_n}" = "1" ] || echo "--force の 2 回目で toml の versions の katachi が ${_n} 件（期待 1）"
	_n=`grep -c '^katachi = { module' "${_t}"`
	[ "${_n}" = "1" ] || echo "--force の 2 回目で toml の libraries の katachi が ${_n} 件（期待 1）"
	_n=`grep -c '^katachiKonsist = ' "${_t}"`
	[ "${_n}" = "1" ] || echo "--force の 2 回目で toml の katachiKonsist が ${_n} 件（期待 1）"
	_n=`grep -c '^katachi = { id' "${_t}"`
	[ "${_n}" = "1" ] || echo "--force の 2 回目で toml の plugins の katachi が ${_n} 件（期待 1）"
	_n=`grep -c '^junit-bom = ' "${_t}"`
	[ "${_n}" = "1" ] || echo "--force の 2 回目で toml の junit-bom が ${_n} 件（期待 1）"
	return 0
}

# ---- 14. kotest の検出（catalog に kotest-runner-junit5、app の build が参照）。FunSpec の雛形になる ----
fx_desc_kotestdetect() { echo "catalog に kotest-runner-junit5（検出して FunSpec の雛形、依存は kotest）"; }
fx_anchor_kotestdetect() { echo 'include(":app")'; }
fx_kotestdetect() {
	d="$1"
	mkdir -p "$d/app/src/main/kotlin/fx"
	cat >"$d/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"
kotest = "${KOTEST}"

[libraries]
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }

[plugins]
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
EOF
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-kotestdetect"
include(":app")
EOF
	printf 'plugins {\n    alias(libs.plugins.kotlinJvm) apply false\n}\n' >"$d/build.gradle.kts"
	printf 'plugins {\n    alias(libs.plugins.kotlinJvm)\n}\ndependencies {\n    testImplementation(libs.kotest.runner.junit5)\n}\n' >"$d/app/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/app/src/main/kotlin/fx/A.kt"
}
fx_expect_kotestdetect() {
	_b="$1/architecture-test/build.gradle.kts"
	_k="$1/architecture-test/src/test/kotlin/com/example/fx/test/architecture/ProjectArchitectureTest.kt"
	[ -f "${_k}" ] || { echo "ProjectArchitectureTest.kt が無い"; return 0; }
	grep -q 'class ProjectArchitectureTest : FunSpec({' "${_k}" || echo "雛形が FunSpec でない"
	grep -q '^import io.kotest.core.spec.style.FunSpec$' "${_k}" || echo "FunSpec の import が無い"
	grep -q 'libs.kotest.runner.junit5' "${_b}" || echo "build.gradle.kts に libs.kotest.runner.junit5 が無い"
	grep -q 'junit.jupiter' "${_b}" && echo "kotest なのに junit-jupiter が書かれている"
	grep -q 'useJUnitPlatform()' "${_b}" || echo "useJUnitPlatform() が無い"
	return 0
}

# ---- 15. toolchain の val 追跡（build-logic の convention plugin が jvmToolchain(jvmTarget)）。JDK 21 だけの環境 ----
fx_desc_tcval() { echo "build-logic の convention plugin に private val jvmTarget = 21 + jvmToolchain(jvmTarget)（JDK 21 だけ）"; }
fx_anchor_tcval() { echo 'include(":app")'; }
fx_toolchain_tcval() { echo 21; }
fx_build_logic() { # $1 = fixture dir, $2 = build-logic/build.gradle.kts の dependencies の 1 行
	mkdir -p "$1/build-logic/src/main/kotlin"
	cat >"$1/build-logic/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "build-logic"
EOF
	printf 'plugins {\n    `kotlin-dsl`\n}\ndependencies {\n    %s\n}\n' "$2" >"$1/build-logic/build.gradle.kts"
}
fx_tcval() {
	d="$1"
	mkdir -p "$d/app/src/main/kotlin/fx"
	cat >"$d/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"

[libraries]
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
EOF
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-tcval"
includeBuild("build-logic")
include(":app")
EOF
	fx_build_logic "$d" 'implementation(libs.kotlin.gradle.plugin)'
	cat >"$d/build-logic/src/main/kotlin/fx.kotlin-conventions.gradle.kts" <<'EOF'
plugins {
    kotlin("jvm")
}

private val jvmTarget = 21

kotlin {
    jvmToolchain(jvmTarget)
}
EOF
	printf 'plugins {\n    id("fx.kotlin-conventions")\n}\n' >"$d/build.gradle.kts"
	printf 'plugins {\n    id("fx.kotlin-conventions")\n}\n' >"$d/app/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/app/src/main/kotlin/fx/A.kt"
	fx_only_running_jdk "$d"
}

# ---- 16. includeBuild の build-logic に Kotlin plugin があり、ルートは convention を apply false で持つだけ ----
# placement は root（architecture-test は版なしの kotlin("jvm")）。--deps literal にして catalog の追記は catalogwrite に任せる。
fx_desc_buildlogic() { echo "includeBuild の build-logic に kotlin-gradle-plugin（ルートは convention を apply false。placement root）"; }
fx_anchor_buildlogic() { echo 'include(":app")'; }
fx_scaffold_args_buildlogic() { echo "--deps literal"; }
fx_buildlogic() {
	d="$1"
	mkdir -p "$d/app/src/main/kotlin/fx"
	cat >"$d/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"

[libraries]
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
EOF
	cat >"$d/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "fx-buildlogic"
includeBuild("build-logic")
include(":app")
EOF
	fx_build_logic "$d" 'implementation(libs.kotlin.gradle.plugin)'
	printf 'plugins {\n    kotlin("jvm")\n}\n' >"$d/build-logic/src/main/kotlin/fx.kotlin-conventions.gradle.kts"
	printf 'plugins {\n    id("fx.kotlin-conventions") apply false\n}\n' >"$d/build.gradle.kts"
	printf 'plugins {\n    id("fx.kotlin-conventions")\n}\n' >"$d/app/build.gradle.kts"
	printf 'package fx\nclass A\n' >"$d/app/src/main/kotlin/fx/A.kt"
}
fx_expect_buildlogic() {
	_b="$1/architecture-test/build.gradle.kts"
	grep -q '^    kotlin("jvm")$' "${_b}" || echo "architecture-test の kotlin(\"jvm\") が版なしでない（placement が root でない）"
	grep -q 'me.tbsten.katachi:katachi:' "${_b}" || echo "--deps literal なのに座標の直書きが無い"
	grep -q 'katachi' "$1/gradle/libs.versions.toml" && echo "--deps literal なのに toml に書いた"
	return 0
}
