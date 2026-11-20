import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

class AndroidReleaseTest(unittest.TestCase):
    def test_update_metadata_matches_android_release(self):
        root = Path(__file__).resolve().parents[1]
        manifest = ET.parse(root / 'android/AndroidManifest.xml').getroot()
        metadata = json.loads((root / 'updates/latest.json').read_text(encoding='utf-8'))
        namespace = '{http://schemas.android.com/apk/res/android}'
        self.assertEqual(metadata['package'], manifest.attrib['package'])
        self.assertEqual(metadata['version_code'], int(manifest.attrib[namespace + 'versionCode']))
        self.assertEqual(metadata['version_name'], manifest.attrib[namespace + 'versionName'])
        self.assertGreater(metadata['version_code'], 0)
