#!/usr/bin/env python3
"""Prepare the engine artifact for the Android build.

The application never builds the core and never reads a sibling checkout: it
consumes exactly one published artifact, identified by version and SHA-256 in
``core-release.lock.json``. This script verifies that identity and installs the
artifact into ``engine/libs/`` (a vendor payload, never committed).

    python3 scripts/prepare_core.py --from <path-to-aar>   # install a built AAR
    python3 scripts/prepare_core.py --offline              # re-verify the payload

Exit 0 on success, exit 1 with ``ERROR: <message>`` lines otherwise, like the
core repository's ``scripts/check_aar.py``.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = REPO_ROOT / "core-release.lock.json"
# The payload is a local Maven repository, so the build resolves the artifact as
# a normal module dependency (`dev.ironjanowar:fretboard-engine:<version>`).
PAYLOAD_DIR = REPO_ROOT / "engine" / "maven" / "dev" / "ironjanowar" / "fretboard-engine"
SUPPORTED_ABIS = ["arm64-v8a", "x86_64"]
EMBEDDED_METADATA = "META-INF/fretboard-engine/metadata.json"


class Report:
    """Collects the problems found while preparing the artifact."""

    def __init__(self) -> None:
        self.problems: list[str] = []

    def reject(self, message: str) -> None:
        self.problems.append(message)

    def emit(self) -> int:
        for problem in self.problems:
            print(f"ERROR: {problem}", file=sys.stderr)
        return 1 if self.problems else 0


def read_lock(report: Report) -> dict | None:
    """Read the release lock, rejecting placeholders instead of trusting them."""
    try:
        lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        report.reject(f"core-release.lock.json cannot be read: {error}")
        return None

    for field in ("artifact_version", "sha256", "uniffi_runtime_dependency", "source_commit"):
        value = lock.get(field)
        if not isinstance(value, str) or not value or value.lower() in {"todo", "tbd", "placeholder", "latest"}:
            report.reject(f"core-release.lock.json has no usable {field!r}")
    if lock.get("abis") != SUPPORTED_ABIS:
        report.reject(
            f"core-release.lock.json 'abis' must be exactly {SUPPORTED_ABIS!r}"
        )
    digest = lock.get("sha256", "")
    if not (len(digest) == 64 and all(character in "0123456789abcdef" for character in digest)):
        report.reject("core-release.lock.json sha256 must be 64 lowercase hex characters")
    return lock


def digest_of(path: Path) -> str:
    """The SHA-256 of a file, read in bounded chunks."""
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def check_payload(path: Path, lock: dict, report: Report) -> None:
    """Check one artifact against the lock and the layout the build needs."""
    actual = digest_of(path)
    if actual != lock["sha256"]:
        report.reject(
            f"the artifact SHA-256 does not match the lock: {actual} != {lock['sha256']}"
        )
        return
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            native_abis = sorted({
                name.split("/")[1]
                for name in names
                if len(name.split("/")) >= 3
                and name.startswith("jni/")
                and name.endswith(".so")
            })
            if native_abis != sorted(SUPPORTED_ABIS):
                report.reject(
                    f"the artifact native ABIs are {native_abis!r}, "
                    f"expected exactly {SUPPORTED_ABIS!r}"
                )
            for abi in SUPPORTED_ABIS:
                native_library = f"jni/{abi}/libfretboard_mobile_ffi.so"
                if native_library not in names:
                    report.reject(f"the artifact carries no {native_library}")
            if EMBEDDED_METADATA not in names:
                report.reject(f"the artifact carries no {EMBEDDED_METADATA}")
                return
            metadata = json.loads(archive.read(EMBEDDED_METADATA).decode("utf-8"))
    except (zipfile.BadZipFile, OSError, json.JSONDecodeError) as error:
        report.reject(f"the artifact cannot be read: {error}")
        return

    for field, expected in (
        ("artifact_version", lock["artifact_version"]),
        ("source_commit", lock["source_commit"]),
        ("uniffi_runtime_dependency", lock["uniffi_runtime_dependency"]),
    ):
        if metadata.get(field) != expected:
            report.reject(
                f"the artifact metadata {field!r} is {metadata.get(field)!r}, "
                f"the lock says {expected!r}"
            )
    if metadata.get("abis") != lock["abis"]:
        report.reject(
            f"the artifact metadata 'abis' is {metadata.get('abis')!r}, "
            f"the lock says {lock['abis']!r}"
        )


POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.ironjanowar</groupId>
  <artifactId>fretboard-engine</artifactId>
  <version>{version}</version>
  <packaging>aar</packaging>
  <name>Fretboard engine</name>
  <description>The generated UniFFI bindings and native engine for Android.</description>
  <dependencies>
    <dependency>
      <groupId>net.java.dev.jna</groupId>
      <artifactId>jna</artifactId>
      <version>{jna}</version>
      <type>aar</type>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
</project>
"""


def install(source: Path, lock: dict, report: Report) -> int:
    """Verify the source artifact and install it into the local repository."""
    check_payload(source, lock, report)
    if report.problems:
        return report.emit()

    version = lock["artifact_version"]
    directory = PAYLOAD_DIR / version
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / f"fretboard-engine-{version}.aar"
    temporary = target.with_suffix(".aar.partial")
    shutil.copyfile(source, temporary)
    # A verified install replaces the payload atomically.
    temporary.replace(target)

    coordinates = lock["uniffi_runtime_dependency"].split(":")
    (directory / f"fretboard-engine-{version}.pom").write_text(
        POM.format(version=version, jna=coordinates[-1]), encoding="utf-8"
    )
    print(f"installed {target.relative_to(REPO_ROOT)}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Prepare the engine artifact.")
    parser.add_argument("--from", dest="source", help="a locally built engine AAR")
    parser.add_argument("--offline", action="store_true", help="re-verify the payload only")
    arguments = parser.parse_args(argv)

    report = Report()
    lock = read_lock(report)
    if lock is None or report.problems:
        return report.emit()

    version = lock["artifact_version"]
    target = PAYLOAD_DIR / version / f"fretboard-engine-{version}.aar"

    if arguments.source:
        source = Path(arguments.source)
        if not source.is_file():
            report.reject(f"the source artifact does not exist: {source}")
            return report.emit()
        return install(source, lock, report)

    if not target.is_file():
        report.reject(
            f"the engine payload is missing: {target.relative_to(REPO_ROOT)}. "
            f"Install it with 'python3 scripts/prepare_core.py --from <aar>'."
        )
        return report.emit()

    check_payload(target, lock, report)
    if not report.problems:
        print(f"verified {target.relative_to(REPO_ROOT)}")
    return report.emit()


if __name__ == "__main__":
    sys.exit(main())
