"""The release guardrails: one RED case per rule, then this repository (task `A22`).

Each rule runs on a throwaway repository that contains exactly the failure it is meant to
catch, and the last test runs the whole checker against this repository — the guardrail that
has to keep holding before a release.
"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_release  # noqa: E402

BUILD = "app/build.gradle.kts"


class ReleaseRuleCase(unittest.TestCase):
    """A throwaway repository the rule under test can fail in."""

    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory()
        self.root = Path(self._temporary.name)
        self.write(BUILD, 'android {\n    versionCode = 12\n    versionName = "0.7.2"\n}\n')
        for name in check_release.REQUIRED_FILES:
            self.write(name, "present\n")

    def tearDown(self) -> None:
        self._temporary.cleanup()

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def rules_fired(self) -> set[str]:
        return {finding.rule for finding in check_release.findings_for(self.root)}


class DeclaredVersionTest(ReleaseRuleCase):
    def test_a_zero_version_code_is_a_finding(self) -> None:
        self.write(BUILD, 'android {\n    versionCode = 0\n    versionName = "0.7.2"\n}\n')

        self.assertIn("release-version", self.rules_fired())

    def test_a_version_name_that_is_not_semantic_is_a_finding(self) -> None:
        self.write(BUILD, 'android {\n    versionCode = 12\n    versionName = "0.7"\n}\n')

        self.assertIn("release-version", self.rules_fired())

    def test_a_proper_version_is_not_a_finding(self) -> None:
        self.assertEqual(set(), self.rules_fired())


class RequiredFileTest(ReleaseRuleCase):
    def test_a_missing_checklist_is_a_finding(self) -> None:
        (self.root / "docs/release-checklist.md").unlink()

        self.assertIn("release-file", self.rules_fired())


class KeepRuleTest(ReleaseRuleCase):
    def test_a_blanket_keep_rule_is_a_finding(self) -> None:
        # Checked where it is in force: with shrinking off nothing reads these rules, so the
        # rule is conditional rather than a demand for a file the build does not use.
        self.write(BUILD, 'android {\n    versionCode = 12\n    versionName = "0.7.2"\n    isMinifyEnabled = true\n}\n')
        self.write(check_release.NATIVE_LOADING_TEST, "// the device test\n")
        self.write("app/proguard-rules.pro", "-keep class ** { *; }\n")

        self.assertIn("narrow-keep-rules", self.rules_fired())


class EarnedShrinkingTest(ReleaseRuleCase):
    def test_shrinking_without_a_device_test_is_a_finding(self) -> None:
        self.write(BUILD, 'android {\n    versionCode = 12\n    versionName = "0.7.2"\n    isMinifyEnabled = true\n}\n')

        self.assertIn("earned-shrinking", self.rules_fired())

    def test_shrinking_with_the_device_test_present_is_not_a_finding(self) -> None:
        self.write(BUILD, 'android {\n    versionCode = 12\n    versionName = "0.7.2"\n    isMinifyEnabled = true\n}\n')
        self.write(check_release.NATIVE_LOADING_TEST, "// the device test that loads the binding through shrinking\n")

        self.assertNotIn("earned-shrinking", self.rules_fired())


class TheRepositoryItselfTest(unittest.TestCase):
    def test_this_repository_passes_the_release_rules(self) -> None:
        findings = check_release.findings_for(Path(__file__).resolve().parent.parent.parent)

        self.assertEqual([], [str(finding) for finding in findings])


if __name__ == "__main__":
    unittest.main()
