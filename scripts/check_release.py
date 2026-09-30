#!/usr/bin/env python3
"""Release guardrails for this repository (task `A22`, second half).

Like the boundary rules, these are guardrails plus review: they check what a release must
declare and must not carry, and each one has a RED case in
`scripts/tests/test_check_release.py`.

Run: `python3 scripts/check_release.py` from the repository root, optionally with the built
APK as an argument, in which case its declared version is compared with the build file's and
its signature is checked with `apksigner`.

Exit code 0 when every rule holds, 1 with one line per finding.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

BUILD_FILE = Path("app/build.gradle.kts")
LOCK_FILE = Path("core-release.lock.json")

# A release names its version; a moving one cannot be released.
VERSION_CODE = re.compile(r"versionCode\s*=\s*(\d+)")
VERSION_NAME = re.compile(r'versionName\s*=\s*"([^"]+)"')
SEMANTIC_VERSION = re.compile(r"^\d+\.\d+\.\d+$")

# Files a release needs: the pin it carries and the checklist that says what a release is
# verified with. The shrinking rules are *not* in this list: they are required only when
# shrinking is on, and today it is off. Gradle dependency verification is also absent from
# this repository — recorded as a gap in the checklist rather than demanded by a rule that
# would be satisfied by a hand-written file.
REQUIRED_FILES = (
    "core-release.lock.json",
    "docs/release-checklist.md",
)

# A blanket keep rule defeats shrinking and hides what is actually needed.
BLANKET_KEEP = re.compile(r"-keep\s+(class\s+\*\*|public\s+class\s+\*\*)")

# Release shrinking may only be on when the native binding is loaded and tested on a device:
# that test's absence is the signal that nobody has done it.
MINIFY_ENABLED = re.compile(r"isMinifyEnabled\s*=\s*true")
NATIVE_LOADING_TEST = "app/src/androidTest/java/dev/ironjanowar/fretboard/release/ReleaseNativeLoadingTest.kt"


@dataclass(frozen=True)
class Finding:
    """One rule that does not hold."""

    rule: str
    path: str
    detail: str

    def __str__(self) -> str:
        return f"{self.rule}: {self.path}: {self.detail}"


def declared_version(root: Path) -> tuple[str, str]:
    """The versionCode and versionName the build file declares."""
    text = (root / BUILD_FILE).read_text()
    code = VERSION_CODE.search(text)
    name = VERSION_NAME.search(text)
    return (code.group(1) if code else "", name.group(1) if name else "")


def check_declared_version(root: Path = ROOT) -> list[Finding]:
    code, name = declared_version(root)
    findings = []
    if not code or int(code) <= 0:
        findings.append(Finding("release-version", str(BUILD_FILE), f"versionCode is {code!r}"))
    if not SEMANTIC_VERSION.match(name):
        findings.append(
            Finding("release-version", str(BUILD_FILE), f"versionName is {name!r}, not major.minor.patch")
        )
    return findings


def check_required_files(root: Path = ROOT) -> list[Finding]:
    return [
        Finding("release-file", name, "a release needs this file")
        for name in REQUIRED_FILES
        if not (root / name).exists()
    ]


def check_keep_rules_are_narrow(root: Path = ROOT) -> list[Finding]:
    """Keep rules, checked only where they are in force: shrinking off means nothing uses them."""
    if not MINIFY_ENABLED.search((root / BUILD_FILE).read_text()):
        return []

    findings = []
    for name in ("app/proguard-rules.pro", "engine/consumer-rules.pro"):
        path = root / name
        if not path.exists():
            continue
        text = path.read_text()
        if BLANKET_KEEP.search(text):
            findings.append(
                Finding("narrow-keep-rules", name, "a blanket keep rule defeats shrinking")
            )
    return findings


def check_shrinking_is_earned(root: Path = ROOT) -> list[Finding]:
    build = root / BUILD_FILE
    if not build.exists() or not MINIFY_ENABLED.search(build.read_text()):
        return []
    if (root / NATIVE_LOADING_TEST).exists():
        return []
    return [
        Finding(
            "earned-shrinking",
            str(BUILD_FILE),
            "release shrinking is on while no device test loads the native binding through it",
        )
    ]


def check_apk_matches(root: Path = ROOT, apk: str | None = None) -> list[Finding]:
    """The built artifact, when one is given: version and signature."""
    if apk is None:
        return []

    path = Path(apk)
    if not path.exists():
        return [Finding("release-apk", apk, "the APK does not exist")]

    findings = []
    _, declared_name = declared_version(root)

    aapt2 = shutil.which("aapt2")
    if aapt2 is None:
        findings.append(Finding("release-apk", apk, "aapt2 is not on PATH: the APK was not inspected"))
    else:
        result = subprocess.run(
            [aapt2, "dump", "badging", str(path)], capture_output=True, text=True, check=False
        )
        declared = re.search(r"versionName='([^']+)'", result.stdout)
        if declared is None or declared.group(1) != declared_name:
            findings.append(
                Finding(
                    "release-apk",
                    apk,
                    f"the APK declares {declared.group(1) if declared else 'nothing'}, the build file {declared_name}",
                )
            )

    apksigner = shutil.which("apksigner")
    if apksigner is None:
        findings.append(Finding("release-apk", apk, "apksigner is not on PATH: the APK was not verified"))
    else:
        result = subprocess.run(
            [apksigner, "verify", "--print-certs", str(path)], capture_output=True, text=True, check=False
        )
        if result.returncode != 0:
            findings.append(Finding("release-apk", apk, "the APK does not verify"))
    return findings


RULES = (
    check_declared_version,
    check_required_files,
    check_keep_rules_are_narrow,
    check_shrinking_is_earned,
)


def findings_for(root: Path = ROOT, apk: str | None = None) -> list[Finding]:
    findings: list[Finding] = []
    for rule in RULES:
        findings.extend(rule(root))
    findings.extend(check_apk_matches(root, apk))
    return findings


def main(argv: list[str]) -> int:
    apk = argv[1] if len(argv) > 1 else None
    findings = findings_for(apk=apk)
    for finding in findings:
        print(finding)
    if findings:
        print(f"{len(findings)} release finding(s)")
        return 1
    print("release rules hold")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
