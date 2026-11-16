import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class PackageResourcePackTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location(
            "package_resource_pack", ROOT / "tools/package_resource_pack.py")
        self.builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.builder)
        directory = ROOT / "target/packaging-tests"
        directory.mkdir(parents=True, exist_ok=True)
        self.workspace = tempfile.TemporaryDirectory(dir=directory)
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        self.source = self.root / "resource-pack"
        self.write("pack.mcmeta", {"pack": {"pack_format": 88, "description": "Test"}})
        self.write("assets/3dgames/items/tabletop/mahjong/tile.json", {
            "model": {"type": "minecraft:model", "model": "3dgames:item/tabletop/mahjong/tile"}})
        self.write("assets/3dgames/models/item/tabletop/mahjong/tile.json", {"textures": {}, "elements": []})
        self.write("assets/3dgames/sounds.json", {})
        self.write("licenses/notice.txt", "Test attribution")
        registration = self.source / "craftengine/resources/3dgames/configuration/tabletop/items.yml"
        registration.parent.mkdir(parents=True)
        registration.write_text("items:\n  3dgames:tile:\n    material: paper\n    item_model: 3dgames:tabletop/mahjong/tile\n")
        (self.root / "src/main/resources").mkdir(parents=True)

    def write(self, name, data):
        path = self.source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data if isinstance(data, str) else json.dumps(data), encoding="utf-8")
        return path

    def test_static_files_are_copied_without_rendering_or_rewriting(self):
        self.write("originals/private.txt", "Authoring material")
        self.write("sources.json", {"originals": []})
        before = {p.relative_to(self.source).as_posix(): p.read_bytes()
                  for p in self.source.rglob("*") if p.is_file()}
        manifest = self.builder.package_resource_pack(self.root)
        with zipfile.ZipFile(self.root / "target/3DGames-resource-pack.zip") as archive:
            expected = {("licenses/3dgames/" + name.removeprefix("licenses/") if name.startswith("licenses/") else name): data for name, data in before.items()
                        if name == "pack.mcmeta" or name.startswith(("assets/", "licenses/"))}
            self.assertEqual(expected, {name: archive.read(name) for name in archive.namelist()})
        self.assertEqual(before, {p.relative_to(self.source).as_posix(): p.read_bytes()
                                 for p in self.source.rglob("*") if p.is_file()})
        self.assertEqual(["tile"], manifest["models"])
        self.assertEqual(manifest["sha1"],
                         (self.root / "src/main/resources/resource-pack.sha1").read_text().strip())

    def test_repacking_identical_files_has_an_identical_checksum(self):
        first = self.builder.package_resource_pack(self.root)
        self.write("pack.mcmeta", {"pack": {"pack_format": 88, "description": "Test"}})
        second = self.builder.package_resource_pack(self.root)
        self.assertEqual(first["sha256"], second["sha256"])
        self.write("licenses/notice.txt", "Updated attribution")
        self.assertNotEqual(first["sha256"], self.builder.package_resource_pack(self.root)["sha256"])

    def test_descriptive_filenames_preserve_registered_logical_ids(self):
        item = self.source / "assets/3dgames/items/tabletop/mahjong/tile.json"
        model = self.source / "assets/3dgames/models/item/tabletop/mahjong/tile.json"
        new_model = "3dgames:item/tabletop/mahjong/mahjong_tile_face"
        self.write("assets/3dgames/items/tabletop/mahjong/mahjong_tile_face.json",
                   {"model": {"type": "minecraft:model", "model": new_model}})
        item.unlink()
        model.rename(model.with_name("mahjong_tile_face.json"))
        registration = self.source / "craftengine/resources/3dgames/configuration/tabletop/items.yml"
        registration.write_text(registration.read_text().replace(
            "3dgames:tabletop/mahjong/tile", "3dgames:tabletop/mahjong/mahjong_tile_face"))
        try:
            manifest = self.builder.package_resource_pack(self.root)
        except ValueError as error:
            self.fail("Descriptive filenames must preserve logical item IDs: " + str(error))
        self.assertEqual(["tile"], manifest["models"])
        with zipfile.ZipFile(self.root / "target/3DGames-resource-pack.zip") as archive:
            self.assertIn("assets/3dgames/items/tabletop/mahjong/mahjong_tile_face.json", archive.namelist())

    def test_missing_item_model_prevents_publishing_a_broken_pack(self):
        (self.source / "assets/3dgames/models/item/tabletop/mahjong/tile.json").unlink()
        with self.assertRaisesRegex(ValueError, "model"):
            self.builder.package_resource_pack(self.root)
        self.assertFalse((self.root / "target/3DGames-resource-pack.zip").exists())

    def test_unregistered_item_prevents_publishing_a_broken_pack(self):
        self.write("assets/3dgames/items/tabletop/mahjong/unknown.json", {
            "model": {"type": "minecraft:model", "model": "3dgames:item/tabletop/mahjong/tile"}})
        with self.assertRaisesRegex(ValueError, "registration"):
            self.builder.package_resource_pack(self.root)

    def test_stale_craftengine_item_path_prevents_publishing_a_broken_pack(self):
        registration = self.source / "craftengine/resources/3dgames/configuration/tabletop/items.yml"
        registration.write_text(registration.read_text().replace(
            "item_model: 3dgames:tabletop/mahjong/tile", "item_model: 3dgames:tile"))
        with self.assertRaisesRegex(ValueError, "item-model registration"):
            self.builder.package_resource_pack(self.root)
        self.assertFalse((self.root / "target/3DGames-resource-pack.zip").exists())

    def test_nested_registration_files_are_combined_without_changing_logical_ids(self):
        registration = self.source / "craftengine/resources/3dgames/configuration/tabletop/items.yml"
        nested = registration.parent / "mahjong/items.yml"
        nested.parent.mkdir(parents=True)
        registration.rename(nested)
        self.write("assets/3dgames/items/casino/blackjack/counter.json", {
            "model": {"type": "minecraft:model", "model": "3dgames:item/casino/blackjack/counter"}})
        self.write("assets/3dgames/models/item/casino/blackjack/counter.json", {"textures": {}, "elements": []})
        self.write("craftengine/resources/3dgames/configuration/casino/items.yml",
                   "items:\n  3dgames:counter:\n    material: paper\n    item_model: 3dgames:casino/blackjack/counter\n")
        try:
            manifest = self.builder.package_resource_pack(self.root)
        except (OSError, ValueError) as error:
            self.fail("Nested registration files must form one logical catalog: " + str(error))
        self.assertEqual(["counter", "tile"], manifest["models"])

    def test_duplicate_logical_ids_across_registration_files_are_rejected(self):
        self.write("craftengine/resources/3dgames/configuration/shared/items.yml",
                   "items:\n  3dgames:tile:\n    material: paper\n    item_model: 3dgames:tabletop/mahjong/tile\n")
        with self.assertRaisesRegex(ValueError, "Duplicate.*tile"):
            self.builder.package_resource_pack(self.root)
        self.assertFalse((self.root / "target/3DGames-resource-pack.zip").exists())


    def test_missing_font_bitmap_prevents_publishing(self):
        self.write("assets/3dgames/font/menu.json", {"providers": [{"type": "bitmap", "file": "3dgames:menu/missing.png"}]})
        with self.assertRaisesRegex(ValueError, "Missing font texture"):
            self.builder.package_resource_pack(self.root)

    def test_missing_model_parent_prevents_publishing(self):
        self.write("assets/3dgames/models/inherited.json", {"parent": "3dgames:missing"})
        with self.assertRaisesRegex(ValueError, "Missing model parent"):
            self.builder.package_resource_pack(self.root)

    def test_license_archive_paths_are_owned_and_preserve_author_notices(self):
        self.builder.package_resource_pack(self.root)
        with zipfile.ZipFile(self.root / "target/3DGames-resource-pack.zip") as archive:
            notices = [name for name in archive.namelist() if name.startswith("licenses/")]
            self.assertEqual(["licenses/3dgames/notice.txt"], notices)
            self.assertEqual(b"Test attribution", archive.read(notices[0]))
            self.assertNotIn("licenses/notice.txt", archive.namelist())

if __name__ == "__main__":
    unittest.main()
