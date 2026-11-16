import json
import math
import unittest
from collections import Counter
from pathlib import Path
from tools.package_resource_pack import item_definitions


ROOT = Path(__file__).resolve().parents[2]
ITEMS = item_definitions(ROOT / "resource-pack")


def rotate(point, rotation):
    x, y, z = [value - origin for value, origin in zip(point, rotation["origin"])]
    angle = math.radians(rotation["angle"])
    cosine, sine = math.cos(angle), math.sin(angle)
    result = ([x * cosine + z * sine, y, -x * sine + z * cosine]
              if rotation["axis"] == "y" else
              [x * cosine - y * sine, x * sine + y * cosine, z])
    return [value + origin for value, origin in zip(result, rotation["origin"])]


class ModelSeamsTest(unittest.TestCase):
    def check_perimeters(self, name, axis):
        item = json.loads(ITEMS[name].read_text())
        path = ROOT / "resource-pack/assets" / (item["model"]["model"].replace(":", "/models/") + ".json")
        model = json.loads(path.read_text())
        endpoints = Counter()
        for part in model["elements"]:
            if part.get("rotation", {}).get("axis") != axis or len(part["faces"]) != 1:
                continue
            if axis == "y" and not {"north", "south", "east", "west"}.intersection(part["faces"]):
                continue
            a, b = part["from"][:], part["to"][:]
            b[1 if axis == "y" else 2] = a[1 if axis == "y" else 2]
            for point in (a, b):
                endpoints[tuple(round(value, 7) for value in rotate(point, part["rotation"]))] += 1
        self.assertTrue(endpoints, name)
        self.assertEqual({2}, set(endpoints.values()), name + " has an open contour or duplicate wall")

    def test_horizontal_shell_walls_join_at_the_same_vertices(self):
        for name in ("card_table", "stone_black", "chess_black_pawn"):
            with self.subTest(model=name):
                self.check_perimeters(name, "y")

    def test_mahjong_chamfered_body_exterior_forms_a_closed_octagon(self):
        model = json.loads((ROOT / 'resource-pack/assets/3dgames/models/item/tabletop/mahjong/mahjong_table.json').read_text())
        body = [part for part in model['elements'] if part.get('name', '').startswith('table_body')]
        self.assertEqual(7, len(body))
        endpoints = Counter()
        for part in body:
            self.assertTrue({'up', 'down'} <= part['faces'].keys())
            low, high = part['from'], part['to']
            for face in part['faces'].keys() - {'up', 'down'}:
                if face in ('east', 'west'):
                    x = high[0] if face == 'east' else low[0]
                    points = ([x, 0, low[2]], [x, 0, high[2]])
                else:
                    z = high[2] if face == 'south' else low[2]
                    points = ([low[0], 0, z], [high[0], 0, z])
                for point in points:
                    if 'rotation' in part:
                        point = rotate(point, part['rotation'])
                    endpoints[(round(point[0], 7), round(point[2], 7))] += 1
        self.assertEqual(8, len(endpoints))
        self.assertEqual({2}, set(endpoints.values()), 'Chamfer walls must close without drawing inner box faces')

    def test_card_and_connect_four_hole_walls_are_closed(self):
        for name in ("playing_spades_ace", "card_r1", "board_connectfour"):
            with self.subTest(model=name):
                self.check_perimeters(name, "z")


if __name__ == "__main__":
    unittest.main()
