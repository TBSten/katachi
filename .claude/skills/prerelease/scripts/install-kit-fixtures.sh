# check-install-kit.sh が読み込む（単独では実行しない）。
# インストールキット（docs/public/install/katachi-install.sh）を試す、合成の Gradle プロジェクト（fixture）の定義。
#
# docs/AGENTS.md の「public/install/」の節の3種（agp / noroot / rootjvm）と、
# これまでに不具合が出た形を1つずつ。足すときは FIXTURES・fx_<名前>・fx_desc_<名前>・fx_anchor_<名前> を揃える。
#
# 呼び出し側が決めておく変数: REPO（リポジトリの直下）、K（Kotlin の版）、AGP（AGP の版）
#
# fx_toolchain_<名前>（任意）は「scaffold が architecture-test/build.gradle.kts に書くべき jvmToolchain の N」。
# none は「jvmToolchain の行を書かない」。定義した fixture だけ、生成物と突き合わせる。
#
# fx_anchor_<名前> は「scaffold が足した include の直前に来るべき行」（前後の空白を除いた中身）。
# END は「include が無いので末尾に足される」。

FIXTURES="agp noroot rootjvm subonly groovyinc multiinc withid buildscript catalogalias jdkonly tc21"

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
