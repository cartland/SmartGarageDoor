#!/usr/bin/env bash
# Check every image the UI gallery manifest references actually exists.
#
# Why: MobileGarage/ui-gallery/manifest.yaml is CURATED BY HAND while the
# images under it are GENERATED. When a capture is renamed or retired the
# manifest keeps pointing at the old path and nothing complains — the gallery
# just renders a broken tile that nobody opens. Empirical: `wear-armed.png`
# was deleted in #1136 (hold-to-press replaced tap-to-arm) and the manifest
# referenced it for months afterwards, found only by hand in 2026-09.
#
# Deliberately dependency-free (no PyYAML — it is not installed on the
# maintainer's machine, which is exactly how `generate-ui-gallery.py --check`
# came to be unrunnable there). Resolves `${alias}/name.png` against the
# `pathAliases:` block with grep and sed only.
#
# Exits non-zero listing every reference that does not resolve.

set -u

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$REPO_ROOT/MobileGarage/ui-gallery/manifest.yaml"

if [ ! -f "$MANIFEST" ]; then
    echo "ERROR: manifest not found at $MANIFEST" >&2
    echo "The gallery moved — update scripts/check-ui-gallery-images.sh." >&2
    exit 1
fi

# --- Resolve the pathAliases block into "alias<TAB>path" lines ---------------
aliases="$(
    awk '
        /^pathAliases:/ { inblock = 1; next }
        inblock && /^[^[:space:]]/ { inblock = 0 }
        inblock && /^[[:space:]]+[A-Za-z0-9_]+:[[:space:]]*[^[:space:]]/ {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            split(line, kv, /:[[:space:]]*/)
            print kv[1] "\t" kv[2]
        }
    ' "$MANIFEST"
)"

if [ -z "$aliases" ]; then
    echo "ERROR: parsed no pathAliases from $MANIFEST." >&2
    echo "This is a check-assumption failure, not a clean pass: the manifest's" >&2
    echo "shape changed and this script's parser needs updating to match." >&2
    exit 1
fi

# --- Every ${alias}/... image reference --------------------------------------
# COMMENT LINES ARE STRIPPED FIRST. The manifest documents its own syntax at
# the top with worked examples (`${wear}/wear-baz.png`), and those placeholders
# are not supposed to exist — counting them made the first run of this script
# report two false failures.
refs="$(sed 's/#.*//' "$MANIFEST" \
    | grep -oE '\$\{[A-Za-z0-9_]+\}/[A-Za-z0-9_./-]+\.(png|webp|jpg)' \
    | sort -u)"

if [ -z "$refs" ]; then
    echo "ERROR: parsed no image references from $MANIFEST." >&2
    echo "Check-assumption failure — see above." >&2
    exit 1
fi

missing=0
checked=0
unknown_alias=0

while IFS= read -r ref; do
    [ -n "$ref" ] || continue
    alias_name="$(printf '%s' "$ref" | sed -E 's/^\$\{([A-Za-z0-9_]+)\}.*/\1/')"
    rest="$(printf '%s' "$ref" | sed -E 's/^\$\{[A-Za-z0-9_]+\}\///')"
    base="$(printf '%s' "$aliases" | awk -F'\t' -v a="$alias_name" '$1 == a { print $2; exit }')"

    if [ -z "$base" ]; then
        echo "UNKNOWN ALIAS: \${$alias_name} in $ref" >&2
        unknown_alias=$((unknown_alias + 1))
        continue
    fi

    checked=$((checked + 1))
    if [ ! -f "$REPO_ROOT/$base/$rest" ]; then
        echo "MISSING: $ref -> $base/$rest" >&2
        missing=$((missing + 1))
    fi
done <<EOF
$refs
EOF

if [ "$missing" -gt 0 ] || [ "$unknown_alias" -gt 0 ]; then
    echo "" >&2
    echo "UI gallery manifest references $missing missing image(s)" >&2
    echo "and $unknown_alias unknown alias(es)." >&2
    echo "" >&2
    echo "The manifest is curated by hand; the images are generated. Either" >&2
    echo "regenerate the captures, repoint the entry, or remove it if the" >&2
    echo "state it showed no longer exists." >&2
    exit 1
fi

echo "UI gallery images OK: $checked reference(s) all resolve."
