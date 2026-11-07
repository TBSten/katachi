#!/usr/bin/env bash
# Replaces the pull request's katachi diff report comment with COMMENT_FILE.
#
# Delete, then create: the fresh comment lands at the end of the timeline, under whatever was said
# since the last push. Editing the old one in place would leave it where it was. The earlier one is
# the comment whose body starts with MARKER.
#
# Environment: GH_TOKEN (pull-requests: write), GITHUB_REPOSITORY, PR (a number or a URL),
# COMMENT_FILE, MARKER.
set -euo pipefail

: "${GH_TOKEN:?}" "${GITHUB_REPOSITORY:?}" "${PR:?}" "${COMMENT_FILE:?}" "${MARKER:?}"
export MARKER

# `https://github.com/o/r/pull/12` and `12` both name pull request 12.
number="${PR%/}"
number="${number##*/}"
case "${number}" in
  '' | *[!0-9]*)
    echo "::error title=katachi diff report::\`${PR}\` is not a pull request number or URL."
    exit 1
    ;;
esac

comments="repos/${GITHUB_REPOSITORY}/issues/${number}/comments"
gh api --paginate "${comments}" --jq '.[] | select(.body | startswith(env.MARKER)) | .id' \
  | while read -r id; do
      echo "Deleting the earlier report comment ${id}"
      gh api -X DELETE "repos/${GITHUB_REPOSITORY}/issues/comments/${id}"
    done
gh api -X POST "${comments}" -F "body=@${COMMENT_FILE}" --jq .html_url
