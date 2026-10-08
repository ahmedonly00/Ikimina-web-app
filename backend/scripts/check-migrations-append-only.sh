#!/bin/sh
# Fails if any migration that already exists on the base revision was modified,
# deleted or renamed (Hard Rule H3). New migrations are the only allowed change.
#
# Flyway would also refuse a changed checksum on a database that already applied
# the file, but CI migrates a fresh database, so this check is what catches it
# before it reaches an environment.
#
#   usage: check-migrations-append-only.sh <base-revision>
#   e.g.   check-migrations-append-only.sh origin/main
set -eu

base="${1:?usage: $0 <base-revision>}"
dir="backend/src/main/resources/db/migration"

# base...HEAD diffs from the merge-base, so migrations that landed on the base
# branch after this branch was cut are not mistaken for deletions here.
# --no-renames reports a rename as delete + add, so the delete is caught.
changes="$(git diff --no-renames --name-status "$base"...HEAD -- "$dir" | grep -v '^A' || true)"

if [ -n "$changes" ]; then
    echo "Existing migrations must never be modified, deleted or renamed. Add a new V<n>__ migration instead:" >&2
    echo "$changes" >&2
    exit 1
fi
echo "Migrations are append-only relative to $base."
