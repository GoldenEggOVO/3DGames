"""Follow registered item IDs through to the actual draught-piece pixels."""
import json
import unittest
from pathlib import Path

from PIL import Image, ImageStat
from tools.package_resource_pack import asset_path, item_definitions


ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "resource-pack"


def resolved_model(logical_id):
    item = json.loads(item_definitions(PACK)[logical_id].read_text(encoding="utf-8"))
    return json.loads(asset_path(PACK, item["model"]["model"], "models", ".json").read_text(encoding="utf-8"))


def used_texture_colors(logical_id):
    model = resolved_model(logical_id)
    references = set()
    for element in model["elements"]:
        for face in element["faces"].values():
            reference = face["texture"]
            while reference.startswith("#"):
                reference = model["textures"][reference[1:]]
            references.add(reference)
    result = {}
    for reference in references:
        with Image.open(asset_path(PACK, reference, "textures", ".png")) as image:
            rgba = image.convert("RGBA")
            result[reference] = ImageStat.Stat(rgba.convert("RGB"), rgba.getchannel("A")).mean
    return result


class DraughtsAssetColorsTest(unittest.TestCase):
    def test_cap_file_and_body_material_names_agree_with_the_registered_side(self):
        for side in ("black", "white"):
            for suffix in ("", "_king"):
                with self.subTest(side=side, piece=suffix or "man"):
                    data = resolved_model("draught_" + side + suffix)
                    caps = {reference for reference in used_texture_colors("draught_" + side + suffix)
                            if "/draughts/" in reference}
                    self.assertEqual({f"3dgames:item/tabletop/draughts/draughts_{side}_piece_top_bottom"}, caps)
                    expected = "POLISHED_BLACKSTONE" if side == "black" else "SMOOTH_QUARTZ"
                    body_aliases = {key for key in data["textures"] if not key.startswith("cap_") and "GOLD" not in key}
                    self.assertEqual({expected, "particle"}, body_aliases)
                    self.assertEqual(data["textures"][expected], data["textures"]["particle"])

    def test_registered_men_and_kings_use_the_actual_semantic_side_color(self):
        for side in ("black", "white"):
            for suffix in ("", "_king"):
                with self.subTest(side=side, piece=suffix or "man"):
                    colors = used_texture_colors("draught_" + side + suffix)
                    body = {name: rgb for name, rgb in colors.items() if "gold" not in name}
                    self.assertGreaterEqual(len(body), 2, "Check both wall material and cap artwork")
                    for reference, rgb in body.items():
                        luminance = .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2]
                        if side == "black":
                            self.assertLess(luminance, 90, reference + " is visibly light")
                        else:
                            self.assertGreater(luminance, 180, reference + " is visibly dark")

    def test_king_gold_ring_stays_distinct_from_the_black_or_white_body(self):
        for side in ("black", "white"):
            colors = used_texture_colors("draught_" + side + "_king")
            ring = {name: rgb for name, rgb in colors.items() if "gold" in name}
            with self.subTest(side=side):
                self.assertGreaterEqual(len(ring), 2, "Retain the independent ring cap and wall")
                for reference, (red, green, blue) in ring.items():
                    self.assertGreater(red - blue, 40, reference)
                    self.assertGreater(green - blue, 30, reference)


if __name__ == "__main__":
    unittest.main()
