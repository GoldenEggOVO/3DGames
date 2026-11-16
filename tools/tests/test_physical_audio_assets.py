"""Physical actions retain independent recorded variations and positional mono samples."""
import hashlib
import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class PhysicalAudioAssetsTest(unittest.TestCase):
    def test_each_physical_event_has_independent_recorded_mono_variants(self):
        sounds = json.loads((ROOT / "resource-pack/assets/3dgames/sounds.json").read_text(encoding="utf-8"))
        for event in ['board.wood', 'board.stone', 'board.drop', 'board.flip', 'board.capture', 'board.glass', 'board.slide', 'dice.contact', 'peg.place', 'peg.feedback', 'board.reveal', 'table.select', 'table.confirm']:
            self.assertIn(event, sounds)
            samples = sounds[event]["sounds"]
            self.assertGreaterEqual(len(samples), 1, event)
            hashes = set()
            for sample in samples:
                self.assertNotIn("pitch", sample)
                ns, name = sample["name"].split(":")
                data = (ROOT / "resource-pack/assets" / ns / "sounds" / (name + ".ogg")).read_bytes()
                self.assertTrue(data.startswith(b"OggS"))
                ident = data.index(b"\x01vorbis")
                self.assertEqual(1, data[ident + 11], event)
                hashes.add(hashlib.sha256(data).hexdigest())
            self.assertEqual(len(samples), len(hashes), event)
    def test_audio_sources_have_exact_archive_member_and_derivation(self):
        source = json.loads((ROOT / "resource-pack/sources.json").read_text(encoding="utf-8"))
        samples = source["physical_audio"]["samples"]
        for event in {item["event"] for item in samples}:
            variations = [item for item in samples if item["event"] == event]
            self.assertEqual(len(variations), len({item["sha256"] for item in variations}), event)
        for item in samples:
            self.assertEqual("CC0-1.0", item["license"])
            self.assertIn("Audio/", item["archive_file"])
            self.assertEqual(item["sha256"], hashlib.sha256((ROOT / item["file"]).read_bytes()).hexdigest())
            self.assertEqual(item["derived_sha256"], hashlib.sha256((ROOT / item["derived_file"]).read_bytes()).hexdigest())

    def test_accepted_compositions_match_retained_dry_masters(self):
        source = json.loads((ROOT / "resource-pack/sources.json").read_text(encoding="utf-8"))["approved_audio"]
        for item in source["samples"]:
            self.assertEqual(item["sha256"], hashlib.sha256((ROOT / item["file"]).read_bytes()).hexdigest())
            self.assertEqual(item["derived_sha256"], hashlib.sha256((ROOT / item["derived_file"]).read_bytes()).hexdigest())
        sounds = json.loads((ROOT / "resource-pack/assets/3dgames/sounds.json").read_text(encoding="utf-8"))
        self.assertEqual(3, len(sounds["board.glass"]["sounds"]))
        self.assertTrue(sounds["board.flip"]["sounds"][0]["name"].endswith("reversi_flip"))
