#!/bin/sh
# リリース前チェックの 9-1 の下ごしらえ: katachi コマンドをリポジトリから組む。
#
#   ./gradlew publishToMavenLocal :katachi-script:cliDist -Pkatachi.skipSigning
#
# cliDist は brew の tarball と同じ形（katachi.main.kts と bin/katachi）を
# katachi-script/build/cli/katachi-cli-<版>/ に置く。katachi.main.kts の @file:DependsOn は ~/.m2/repository を先に見るので、
# publish した katachi-script がそのまま使われる（fixture も KATACHI_MAVEN_LOCAL=1 で同じ mavenLocal から katachi を取る）。
# 動かすには kotlin が PATH に要る（brew install kotlin）。
#
# 出力: 最後の行に bin/katachi の絶対パス。終了コード: 組めたら 0、Gradle が落ちたら 1、kotlin が無ければ 2。
#
# 使い方（リポジトリの直下で。ほかの Gradle のビルドが走っていないことを pgrep -fl GradleWrapperMain で確かめてから）:
#   sh .claude/skills/prerelease/scripts/build-katachi-cli.sh
#
# 決まり: $(...) は使わない（リポジトリの決まり）。$VAR の直後に日本語を置かない（${VAR} で閉じる。bash 3.2 が食う）。
set -u

REPO=`git rev-parse --show-toplevel` || exit 2
VERSION=`sed -n 's/^katachi[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${REPO}/gradle/libs.versions.toml" | head -1`
[ -n "${VERSION}" ] || { echo "gradle/libs.versions.toml から katachi の版を読めませんでした" >&2; exit 2; }

if ! command -v kotlin >/dev/null 2>&1; then
	echo "kotlin が PATH にありません（katachi コマンドは kotlin で katachi.main.kts を動かす）。brew install kotlin で入れてください" >&2
	exit 2
fi

(cd "${REPO}" && ./gradlew publishToMavenLocal :katachi-script:cliDist -Pkatachi.skipSigning --no-daemon --console=plain \
	--project-cache-dir .local/tmp/gradle-cache/prerelease-install) || exit 1

CLI="${REPO}/katachi-script/build/cli/katachi-cli-${VERSION}/bin/katachi"
[ -x "${CLI}" ] || { echo "cliDist の出力がありません: ${CLI}" >&2; exit 1; }
echo "${CLI}"
