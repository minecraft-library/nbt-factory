#!/usr/bin/env bash
#
# Reproducibility script for the simdnbt benchmark corpus.
#
# Vendors the 7 corpus files used by simdnbt's own benchmarks into
# src/test/resources/simdnbt-corpus/, then verifies SHA-256 hashes against the
# pinned commit. CI never runs this; it exists only to document where the bytes
# came from and to allow refreshing them on demand.
#
# Source repo:
#   https://git.matdoes.dev/mat/simdnbt   (mirror: https://github.com/azalea-rs/simdnbt)
# Pinned ref:
#   master @ 4cc67bcd980c   (tagged 0.10.0, 2026-03-28)
# Author:
#   mat (https://matdoes.dev)
# License:
#   MIT - see https://git.matdoes.dev/mat/simdnbt/raw/branch/master/LICENSE
#
# Vendoring research-corpus binary files under MIT is permitted with attribution;
# this script header carries that attribution.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORPUS_DIR="${REPO_ROOT}/src/test/resources/simdnbt-corpus"
BASE_URL="https://git.matdoes.dev/mat/simdnbt/raw/branch/master/simdnbt/tests"

mkdir -p "${CORPUS_DIR}"

# Pinned SHA-256 hashes - these MUST match the bytes vendored at the pinned
# simdnbt commit. Any drift means the upstream corpus moved and the JMH
# baseline numbers in jmh-results/ are no longer comparable to the new bytes.
declare -A EXPECTED_SHA256=(
    [bigtest.nbt]="43629bb139ff0ed46e8dd24f7abbcacf0bc9deededb055d8105b3056ca3fc8df"
    [complex_player.dat]="f2a8694e44de6398d8c2e63b41c165d48c9f85d9fe377a874b4d26e08338a694"
    [hello_world.nbt]="7f27e590592aaaefd0ca0882caae7cdf42421b157325623cc11b22ea1bfbb4c5"
    [hypixel.nbt]="4921665c039768fd11703120d1a9ed33cc4789812a56839e6da65558cc2bf149"
    [inttest1023.nbt]="b2a91718c84a32dfec78f82e54da677c298b8af321fe677bc8a3c51d8b40e794"
    [level.dat]="2d6f0c3b60437329b18699a5d16b6a466b5fe13e7f754187db849a72885eb013"
    [simple_player.dat]="7632963ce6891c95e36f59284b15c1a8f50cefe2b0606c4f4fbcab59f985a8f9"
)

FILES=(
    bigtest.nbt
    complex_player.dat
    hello_world.nbt
    hypixel.nbt
    inttest1023.nbt
    level.dat
    simple_player.dat
)

failed=0
for f in "${FILES[@]}"; do
    out="${CORPUS_DIR}/${f}"
    echo "Fetching ${f}..."
    if ! curl -sSL --fail -o "${out}" "${BASE_URL}/${f}"; then
        echo "  FAILED to download ${f}" >&2
        failed=1
        continue
    fi

    # Reject HTML 404 pages masquerading as binary.
    first_bytes=$(head -c 4 "${out}" | od -An -tx1 | tr -d ' \n')
    if [[ "${first_bytes}" == 3c21444f* || "${first_bytes}" == 3c68746d* ]]; then
        echo "  REJECTED ${f}: response looks like HTML, not a binary fixture" >&2
        rm -f "${out}"
        failed=1
        continue
    fi

    expected="${EXPECTED_SHA256[$f]}"
    actual=$(sha256sum "${out}" | awk '{print $1}')
    if [[ "${actual}" != "${expected}" ]]; then
        echo "  SHA256 MISMATCH ${f}" >&2
        echo "    expected: ${expected}" >&2
        echo "    actual:   ${actual}" >&2
        failed=1
    else
        echo "  OK  ${f}  ($(wc -c < "${out}") bytes)"
    fi
done

if [[ "${failed}" -ne 0 ]]; then
    echo
    echo "One or more files failed to verify. The corpus is in an inconsistent state." >&2
    exit 1
fi

echo
echo "All 7 corpus files verified at ${CORPUS_DIR}"
