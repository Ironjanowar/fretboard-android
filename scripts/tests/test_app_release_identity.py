"""Release identity contract for the launcher icon and Android version."""

import hashlib
import re
import struct
import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = REPOSITORY / "app/src/main/AndroidManifest.xml"
BUILD_SCRIPT = REPOSITORY / "app/build.gradle.kts"
MASTER_ICON = "artwork/launcher-icon-master.jpg"
MASTER_ICON_SHA256 = "e3d473cab439e122b9ab3d241c7cb2729e509f722a743a8848d038ad8054bdcd"
ADAPTIVE_ICONS = (
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
)
DENSITY_SCALES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
RASTER_ICON_DP = {
    "ic_launcher.png": 48,
    "ic_launcher_round.png": 48,
    "ic_launcher_foreground.png": 108,
}
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def png_dimensions(path: Path) -> tuple[int, int]:
    """Read the width and height from a PNG's mandatory IHDR chunk."""
    header = path.read_bytes()[:24]
    if len(header) != 24 or header[:8] != PNG_SIGNATURE or header[12:16] != b"IHDR":
        raise ValueError(f"not a PNG with an IHDR header: {path}")
    return struct.unpack(">II", header[16:24])


class LauncherManifestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.application = ET.parse(MANIFEST).getroot().find("application")
        assert cls.application is not None

    def test_application_declares_launcher_icon(self) -> None:
        self.assertEqual("@mipmap/ic_launcher", self.application.get(f"{ANDROID}icon"))

    def test_application_declares_round_launcher_icon(self) -> None:
        self.assertEqual(
            "@mipmap/ic_launcher_round",
            self.application.get(f"{ANDROID}roundIcon"),
        )


class LauncherResourceTest(unittest.TestCase):
    def test_adaptive_icon_xml_resources_exist(self) -> None:
        missing = [relative for relative in ADAPTIVE_ICONS if not (REPOSITORY / relative).is_file()]

        self.assertEqual([], missing, "missing adaptive icon XML resources")

    def test_adaptive_icons_reference_the_launcher_layers(self) -> None:
        for relative in ADAPTIVE_ICONS:
            with self.subTest(resource=relative):
                root = ET.parse(REPOSITORY / relative).getroot()
                self.assertEqual("adaptive-icon", root.tag)
                self.assertEqual(
                    "@color/ic_launcher_background",
                    root.find("background").get(f"{ANDROID}drawable"),
                )
                self.assertEqual(
                    "@mipmap/ic_launcher_foreground",
                    root.find("foreground").get(f"{ANDROID}drawable"),
                )

    def test_raster_icons_cover_every_density_at_android_dimensions(self) -> None:
        for density, scale in DENSITY_SCALES.items():
            for filename, size_dp in RASTER_ICON_DP.items():
                relative = f"app/src/main/res/mipmap-{density}/{filename}"
                expected = round(size_dp * scale)
                with self.subTest(resource=relative):
                    self.assertEqual((expected, expected), png_dimensions(REPOSITORY / relative))

    def test_master_icon_is_present_and_tracked(self) -> None:
        master = REPOSITORY / MASTER_ICON
        self.assertTrue(master.is_file(), f"missing master icon: {MASTER_ICON}")
        tracked = subprocess.run(
            ["git", "ls-files", "--error-unmatch", MASTER_ICON],
            cwd=REPOSITORY,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )

        self.assertEqual(0, tracked.returncode, f"master icon is not tracked: {MASTER_ICON}")

    def test_master_icon_preserves_the_approved_source_bytes(self) -> None:
        actual = hashlib.sha256((REPOSITORY / MASTER_ICON).read_bytes()).hexdigest()

        self.assertEqual(MASTER_ICON_SHA256, actual)


class ReleaseVersionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.build_script = BUILD_SCRIPT.read_text(encoding="utf-8")

    def test_version_code_is_19(self) -> None:
        declaration = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", self.build_script, re.MULTILINE)

        self.assertIsNotNone(declaration, "app/build.gradle.kts must declare versionCode")
        assert declaration is not None
        self.assertEqual("19", declaration.group(1))

    def test_version_name_is_0_8_6(self) -> None:
        declaration = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', self.build_script, re.MULTILINE)

        self.assertIsNotNone(declaration, "app/build.gradle.kts must declare versionName")
        assert declaration is not None
        self.assertEqual("0.8.6", declaration.group(1))


if __name__ == "__main__":
    unittest.main()
