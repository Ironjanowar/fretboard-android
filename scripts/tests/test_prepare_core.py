"""Tests for `scripts/prepare_core.py`.

The engine payload is the one input this repository trusts from outside, so the
tests are about rejection: a placeholder or stale lock, a mismatched digest, a
missing native library and mismatched embedded metadata must all stop the build
instead of installing something unverified.

Run with: python3 -m unittest discover -s scripts/tests -p 'test_*.py'
"""

from __future__ import annotations

import hashlib
import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import prepare_core  # noqa: E402

ABIS = ["arm64-v8a", "x86_64"]
NATIVE_LIBRARIES = {
    abi: f"jni/{abi}/libfretboard_mobile_ffi.so" for abi in ABIS
}
METADATA = "META-INF/fretboard-engine/metadata.json"

LOCK = {
    "artifact_version": "0.1.0",
    "sha256": "0" * 64,
    "abis": ABIS,
    "uniffi_runtime_dependency": "net.java.dev.jna:jna:5.17.0",
    "source_commit": "39f10d7cd766f3e9c0fcc5ed23164ec3e1be593d",
}


def build_artifact(
    directory: Path,
    lock: dict,
    *,
    native_abis: list[str] | None = None,
    metadata: dict | None = None,
    additional_entries: dict[str, bytes] | None = None,
) -> Path:
    """Write an artifact shaped like the one the core repository publishes."""
    embedded = {
        "artifact_version": lock["artifact_version"],
        "source_commit": lock["source_commit"],
        "uniffi_runtime_dependency": lock["uniffi_runtime_dependency"],
        "abis": lock["abis"],
    }
    embedded.update(metadata or {})

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for abi in lock["abis"] if native_abis is None else native_abis:
            native_library = f"jni/{abi}/libfretboard_mobile_ffi.so"
            archive.writestr(native_library, b"\x7fELF" + b"\x00" * 64)
        for name, contents in (additional_entries or {}).items():
            archive.writestr(name, contents)
        archive.writestr(METADATA, json.dumps(embedded, indent=2))
        archive.writestr("classes.jar", b"PK\x03\x04")
    payload = buffer.getvalue()

    path = directory / "artifact.aar"
    path.write_bytes(payload)
    return path


class PrepareCoreTestCase(unittest.TestCase):
    """Each test drives the script in its own temporary repository."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.lock = dict(LOCK)
        self.lock_path = self.root / "core-release.lock.json"
        self.payload = self.root / "engine" / "maven" / "dev" / "ironjanowar" / "fretboard-engine"
        self.write_lock()
        self.digest = ""

        # Point the script at the temporary repository.
        self.original_lock_path = prepare_core.LOCK_PATH
        self.original_payload_dir = prepare_core.PAYLOAD_DIR
        self.original_repo_root = prepare_core.REPO_ROOT
        prepare_core.LOCK_PATH = self.lock_path
        prepare_core.PAYLOAD_DIR = self.payload
        prepare_core.REPO_ROOT = self.root

    def tearDown(self) -> None:
        prepare_core.LOCK_PATH = self.original_lock_path
        prepare_core.PAYLOAD_DIR = self.original_payload_dir
        prepare_core.REPO_ROOT = self.original_repo_root
        self.temporary.cleanup()

    def write_lock(self) -> None:
        self.lock_path.write_text(json.dumps(self.lock, indent=2), encoding="utf-8")

    def artifact(self, **kwargs) -> Path:
        """Build an artifact and record its real digest in the lock."""
        path = build_artifact(self.root, self.lock, **kwargs)
        self.lock["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.write_lock()
        return path

    def run_script(self, *arguments: str) -> tuple[int, str]:
        """Run the script, returning its exit code and stderr."""
        import contextlib

        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            code = prepare_core.main(list(arguments))
        return code, errors.getvalue()

    def installed(self) -> list[Path]:
        return sorted(path for path in self.payload.rglob("*") if path.is_file())


class LockTest(PrepareCoreTestCase):
    def test_a_missing_lock_stops_the_build(self) -> None:
        self.lock_path.unlink()
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("core-release.lock.json cannot be read", errors)

    def test_a_placeholder_digest_is_rejected(self) -> None:
        self.lock["sha256"] = "todo"
        self.write_lock()
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("no usable 'sha256'", errors)

    def test_a_short_digest_is_rejected(self) -> None:
        self.lock["sha256"] = "abc123"
        self.write_lock()
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("64 lowercase hex characters", errors)

    def test_an_uppercase_digest_is_rejected(self) -> None:
        self.lock["sha256"] = "A" * 64
        self.write_lock()
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("64 lowercase hex characters", errors)

    def test_a_missing_lock_field_is_reported_by_name(self) -> None:
        del self.lock["source_commit"]
        self.write_lock()
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("no usable 'source_commit'", errors)

    def test_a_missing_abis_list_is_rejected(self) -> None:
        del self.lock["abis"]
        self.write_lock()
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("'abis'", errors)

    def test_an_invalid_abis_list_is_rejected(self) -> None:
        invalid_values = (
            "arm64-v8a",
            [],
            ["arm64-v8a"],
            ["x86_64", "arm64-v8a"],
            ["arm64-v8a", "x86_64", "armeabi-v7a"],
        )
        for invalid in invalid_values:
            with self.subTest(abis=invalid):
                self.lock["abis"] = invalid
                self.write_lock()
                code, errors = self.run_script("--offline")
                self.assertEqual(code, 1)
                self.assertIn("'abis'", errors)


class InstallTest(PrepareCoreTestCase):
    def test_a_verified_dual_abi_artifact_is_installed_as_a_local_repository(self) -> None:
        source = self.artifact()
        code, errors = self.run_script("--from", str(source))
        self.assertEqual((code, errors), (0, ""))
        names = [path.name for path in self.installed()]
        self.assertIn("fretboard-engine-0.1.0.aar", names)
        self.assertIn("fretboard-engine-0.1.0.pom", names)

    def test_the_generated_pom_carries_the_runtime_dependency(self) -> None:
        source = self.artifact()
        self.run_script("--from", str(source))
        pom = (self.payload / "0.1.0" / "fretboard-engine-0.1.0.pom").read_text(encoding="utf-8")
        self.assertIn("<artifactId>jna</artifactId>", pom)
        self.assertIn("<version>5.17.0</version>", pom)

    def test_a_wrong_digest_installs_nothing(self) -> None:
        source = self.artifact()
        self.lock["sha256"] = "b" * 64
        self.write_lock()
        code, errors = self.run_script("--from", str(source))
        self.assertEqual(code, 1)
        self.assertIn("does not match the lock", errors)
        self.assertEqual(self.installed(), [])

    def test_an_artifact_without_either_native_library_is_rejected(self) -> None:
        for missing_abi in ABIS:
            with self.subTest(missing_abi=missing_abi):
                present_abis = [abi for abi in ABIS if abi != missing_abi]
                source = self.artifact(native_abis=present_abis)
                code, errors = self.run_script("--from", str(source))
                self.assertEqual(code, 1)
                self.assertIn(f"carries no {NATIVE_LIBRARIES[missing_abi]}", errors)

    def test_an_artifact_with_an_additional_native_abi_is_rejected(self) -> None:
        source = self.artifact(native_abis=[*ABIS, "armeabi-v7a"])
        code, errors = self.run_script("--from", str(source))
        self.assertEqual(code, 1)
        self.assertIn("native ABIs", errors)
        self.assertIn("armeabi-v7a", errors)

    def test_an_unrelated_library_under_an_additional_abi_is_rejected(self) -> None:
        source = self.artifact(
            additional_entries={"jni/armeabi-v7a/libunexpected.so": b"\x7fELF"},
        )
        code, errors = self.run_script("--from", str(source))
        self.assertEqual(code, 1)
        self.assertIn("native ABIs", errors)
        self.assertIn("armeabi-v7a", errors)

    def test_metadata_that_disagrees_with_the_lock_is_rejected(self) -> None:
        source = self.artifact(metadata={"source_commit": "0" * 40})
        code, errors = self.run_script("--from", str(source))
        self.assertEqual(code, 1)
        self.assertIn("'source_commit' is", errors)

    def test_metadata_with_different_abis_is_rejected(self) -> None:
        source = self.artifact(metadata={"abis": ["arm64-v8a"]})
        code, errors = self.run_script("--from", str(source))
        self.assertEqual(code, 1)
        self.assertIn("metadata 'abis'", errors)

    def test_a_missing_source_artifact_is_reported(self) -> None:
        code, errors = self.run_script("--from", str(self.root / "absent.aar"))
        self.assertEqual(code, 1)
        self.assertIn("does not exist", errors)


class OfflineTest(PrepareCoreTestCase):
    def test_an_absent_payload_is_reported_with_the_command_to_fix_it(self) -> None:
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("the engine payload is missing", errors)
        self.assertIn("prepare_core.py --from", errors)

    def test_an_installed_payload_is_verified_without_network_access(self) -> None:
        source = self.artifact()
        self.run_script("--from", str(source))
        code, errors = self.run_script("--offline")
        self.assertEqual((code, errors), (0, ""))

    def test_a_tampered_payload_is_detected_offline(self) -> None:
        source = self.artifact()
        self.run_script("--from", str(source))
        target = self.payload / "0.1.0" / "fretboard-engine-0.1.0.aar"
        target.write_bytes(target.read_bytes() + b"tampered")
        code, errors = self.run_script("--offline")
        self.assertEqual(code, 1)
        self.assertIn("does not match the lock", errors)


if __name__ == "__main__":
    unittest.main()
