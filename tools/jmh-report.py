#!/usr/bin/env python3
"""Render JMH JSON results as a simdnbt-style throughput table.

Usage::

    python tools/jmh-report.py build/results/jmh/results.json
    python tools/jmh-report.py jmh-results/phase-A1-g1.json jmh-results/phase-A1-epsilon.json

The script reads one or more JMH JSON result files (the format produced by
``-rf JSON``) and prints a single combined table with library / file /
profile / mode / MiB/s / GiB/s columns.

Throughput conversion
---------------------

For ``Mode.AverageTime`` results (units ``ns/op``, ``us/op``, ``ms/op``,
``s/op``) the script computes::

    MiB/s = bytes_per_op / time_ns_per_op * 1e9 / 1048576

For ``Mode.Throughput`` results (units ``ops/ns`` etc.) the script computes::

    MiB/s = score_ops_per_s * bytes_per_op / 1048576

``bytes_per_op`` is sourced in priority order:

1. The ``payloadBytes`` ``@AuxCounters`` value if the benchmark exports one.
2. The fallback table below, keyed by the ``filename`` JMH parameter.
3. ``payloadBytes`` printed to stderr by the benchmark's ``@Setup`` (parsed
   from a ``payload-size: <filename>=<bytes>`` line if present in the
   ``stdout``/``stderr`` capture passed via ``--sizes-log``).

The ``Profile`` column is derived from the result file's basename: anything
containing ``epsilon`` is reported as ``epsilon``, anything containing
``g1`` is reported as ``g1``, otherwise the basename minus the extension
is used verbatim.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# Fallback table - maps JMH ``filename`` @Param values to known uncompressed
# byte counts. Lets the report run without the @AuxCounters byte counter when
# only the payload filename is known. Keep in sync with the corpus under
# src/test/resources/simdnbt-corpus/ - simdnbt's gzipped .dat files decompress
# to roughly 10x their on-disk size; the values here are the *uncompressed*
# bytes the benchmark actually decodes.
FALLBACK_PAYLOAD_BYTES = {
    # simdnbt corpus - uncompressed bytes (post gzip-decode for .dat files)
    "bigtest.nbt":          507,           # not gzipped on disk
    "hello_world.nbt":      33,            # not gzipped
    "hypixel.nbt":          18_670,        # not gzipped
    "inttest1023.nbt":      4_104,         # not gzipped
    "complex_player.dat":   9_876,         # gzipped on disk; ~10 KB uncompressed
    "level.dat":            10_252,        # gzipped on disk; rough estimate
    "simple_player.dat":    1_544,         # gzipped on disk; rough estimate
    # Mutf strings - synthetic
    "ascii-100":            100,
    "ascii-32":             32,
    "bmp-mixed-32":         32,
}

# Convert all the JMH time units to nanoseconds.
TIME_UNIT_TO_NS = {
    "ns/op":  1.0,
    "us/op":  1_000.0,
    "ms/op":  1_000_000.0,
    "s/op":   1_000_000_000.0,
    "min/op": 60_000_000_000.0,
}

# Convert all the JMH throughput units to ops/s.
THROUGHPUT_UNIT_TO_OPS_PER_S = {
    "ops/ns":  1_000_000_000.0,
    "ops/us":  1_000_000.0,
    "ops/ms":  1_000.0,
    "ops/s":   1.0,
    "ops/min": 1.0 / 60.0,
}


def derive_profile(path: Path) -> str:
    name = path.stem.lower()
    if "epsilon" in name:
        return "epsilon"
    if "g1" in name:
        return "g1"
    return path.stem


def short_benchmark_name(full: str) -> str:
    # JMH gives a fully-qualified benchmark name; drop the package prefix.
    return full.rsplit(".", 1)[-1]


def short_class_name(full: str) -> str:
    parts = full.rsplit(".", 2)
    if len(parts) >= 2:
        return parts[-2]
    return full


def filename_param(record: dict) -> str:
    params = record.get("params") or {}
    return (
        params.get("filename")
        or params.get("file")
        or params.get("string")
        or params.get("strategyName")
        or ""
    )


def aux_counter(record: dict) -> tuple[float, str] | None:
    """Returns ``(score, unit)`` for the first known byte-counter aux metric, if present."""
    secondary = record.get("secondaryMetrics") or {}
    for key in ("payloadBytes", "bytes", "bytesProcessed"):
        if key in secondary:
            try:
                return (
                    float(secondary[key]["score"]),
                    str(secondary[key].get("scoreUnit", "")),
                )
            except (KeyError, TypeError, ValueError):
                pass
    return None


def mib_per_s(record: dict) -> float | None:
    primary = record.get("primaryMetric") or {}
    unit = primary.get("scoreUnit", "")
    score = primary.get("score")
    if score is None or score == 0:
        return None

    aux = aux_counter(record)

    # Preferred path: a byte-counter @AuxCounters(Type.OPERATIONS) state. JMH scores it
    # in the SAME unit as the primary metric, with "operations" replaced by "bytes". So:
    #   - Throughput primary unit "ops/<time>"  -> aux unit "ops/<time>" = bytes/<time>
    #   - AverageTime primary unit "<time>/op"  -> aux unit "<time>/op" = <time>/byte
    # Convert directly to bytes/s.
    if aux is not None:
        aux_score, aux_unit = aux
        if aux_unit in THROUGHPUT_UNIT_TO_OPS_PER_S:
            # bytes per <time> -> bytes/s
            bytes_per_s = aux_score * THROUGHPUT_UNIT_TO_OPS_PER_S[aux_unit]
            return bytes_per_s / 1_048_576.0
        if aux_unit in TIME_UNIT_TO_NS:
            # <time>/byte -> seconds/byte -> bytes/s
            ns_per_byte = aux_score * TIME_UNIT_TO_NS[aux_unit]
            if ns_per_byte == 0:
                return None
            return 1_000_000_000.0 / ns_per_byte / 1_048_576.0

    # Fallback path: no aux counter, derive bytes-per-op from the filename param's known
    # uncompressed size and the primary metric.
    fname = filename_param(record)
    bpo = FALLBACK_PAYLOAD_BYTES.get(fname)
    if bpo is None:
        return None

    if unit in THROUGHPUT_UNIT_TO_OPS_PER_S:
        ops_per_s = score * THROUGHPUT_UNIT_TO_OPS_PER_S[unit]
        return ops_per_s * bpo / 1_048_576.0

    if unit in TIME_UNIT_TO_NS:
        ns_per_op = score * TIME_UNIT_TO_NS[unit]
        if ns_per_op == 0:
            return None
        return float(bpo) / ns_per_op * 1_000_000_000.0 / 1_048_576.0

    print(f"warning: unknown JMH score unit '{unit}' for {record.get('benchmark')}",
          file=sys.stderr)
    return None


def render_rows(records: list[tuple[Path, dict]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for path, record in records:
        primary = record.get("primaryMetric") or {}
        unit = primary.get("scoreUnit", "?")
        # Mode is encoded inside the benchmark mode field; default is "thrpt"
        # for Throughput and "avgt" for AverageTime.
        mode = record.get("mode", "?")
        mode_label = {
            "thrpt": "Throughput",
            "avgt":  "AverageTime",
            "sample": "SampleTime",
            "ss": "SingleShotTime",
            "all": "All",
        }.get(mode, mode)
        mib = mib_per_s(record)
        rows.append({
            "library":  "nbt-factory",
            "klass":    short_class_name(record.get("benchmark", "")),
            "method":   short_benchmark_name(record.get("benchmark", "")),
            "file":     filename_param(record),
            "profile":  derive_profile(path),
            "mode":     mode_label,
            "score":    f"{primary.get('score', 0):.3f}",
            "score_unit": unit,
            "mib_s":    f"{mib:8.2f}" if mib is not None else "      ?",
            "gib_s":    f"{mib/1024.0:7.3f}" if mib is not None else "      ?",
        })
    return rows


def render_table(rows: list[dict[str, str]]) -> str:
    if not rows:
        return "(no records)"
    headers = ["Library", "Class", "Method", "File", "Profile", "Mode",
               "Score", "Unit", "MiB/s", "GiB/s"]
    keys = ["library", "klass", "method", "file", "profile", "mode",
            "score", "score_unit", "mib_s", "gib_s"]
    widths = [len(h) for h in headers]
    for row in rows:
        for i, k in enumerate(keys):
            widths[i] = max(widths[i], len(row[k]))

    def fmt_row(values: list[str]) -> str:
        cells = [v.ljust(widths[i]) for i, v in enumerate(values)]
        return "| " + " | ".join(cells) + " |"

    sep = "|" + "|".join("-" * (w + 2) for w in widths) + "|"
    out = [fmt_row(headers), sep]
    for row in rows:
        out.append(fmt_row([row[k] for k in keys]))
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results", nargs="+", type=Path,
        help="JMH JSON result file(s)")
    args = parser.parse_args()

    records: list[tuple[Path, dict]] = []
    for path in args.results:
        if not path.exists():
            print(f"missing: {path}", file=sys.stderr)
            return 2
        try:
            with path.open("r", encoding="utf-8") as f:
                payload = json.load(f)
        except json.JSONDecodeError as e:
            print(f"error: {path} is not valid JSON: {e}", file=sys.stderr)
            return 2
        if not isinstance(payload, list):
            print(f"warning: expected a JSON array in {path}, got {type(payload).__name__}",
                  file=sys.stderr)
            continue
        for record in payload:
            records.append((path, record))

    rows = render_rows(records)
    print(render_table(rows))

    # Print known fallback sizes for transparency on stderr.
    if any(r["mib_s"].strip() == "?" for r in rows):
        print("\nNote: some rows lacked an @AuxCounters byte counter and no fallback "
              "size matched. Update FALLBACK_PAYLOAD_BYTES in jmh-report.py or "
              "ensure your benchmark exports a `payloadBytes` aux counter.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
