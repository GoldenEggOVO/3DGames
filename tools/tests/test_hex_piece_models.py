import json
import math
import unittest
from collections import Counter
from pathlib import Path

from PIL import Image
from tools.package_resource_pack import item_definitions

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'resource-pack/assets/3dgames'


class HexPieceModelsTest(unittest.TestCase):
    def model(self, color):
        items = item_definitions(ROOT / 'resource-pack')
        key = 'classic_hex_piece_' + color
        self.assertTrue(key in items, 'Missing registered hex stone: ' + key)
        reference = json.loads(items[key].read_text())['model']['model'].split(':', 1)[1]
        return json.loads((ASSETS / 'models' / (reference + '.json')).read_text())

    def test_caps_follow_the_hexagon_without_changing_the_team_texture(self):
        for color in ('red', 'blue'):
            with self.subTest(color=color):
                data = self.model(color)
                self.assertEqual([2, 2, 2], data['display']['fixed']['scale'])
                cap = next(part for part in data['elements'] if part['name'] == 'hex_cap')
                self.assertEqual({'up', 'down'}, set(cap['faces']))
                self.assertEqual([4, 8, 4], cap['from'])
                self.assertEqual([12, 16, 12], cap['to'])
                reference = data['textures'][cap['faces']['up']['texture'][1:]]
                artwork = Image.open(ASSETS / 'textures' / (reference.split(':', 1)[1] + '.png')).convert('RGBA')
                original = Image.open(ASSETS / f'textures/item/shared/materials/material_{color}_concrete.png').convert('RGBA').resize(artwork.size, Image.Resampling.NEAREST)
                for y in range(artwork.height):
                    for x in range(artwork.width):
                        inside = 2 * abs((x + .5) / artwork.width - .5) + abs((y + .5) / artwork.height - .5) <= 1
                        pixel = artwork.getpixel((x, y))
                        self.assertEqual(255 if inside else 0, pixel[3])
                        if inside:
                            self.assertEqual(original.getpixel((x, y))[:3], pixel[:3])

    def test_six_side_walls_meet_the_same_six_vertices_as_the_caps(self):
        expected = {(12., 8.), (10., 12.), (6., 12.), (4., 8.), (6., 4.), (10., 4.)}
        for color in ('red', 'blue'):
            data = self.model(color)
            walls = [part for part in data['elements'] if part['name'].startswith('hex_wall_')]
            self.assertEqual(6, len(walls))
            self.assertEqual(8, sum(len(part['faces']) for part in data['elements']))
            endpoints = Counter()
            for wall in walls:
                self.assertEqual({'north'}, set(wall['faces']))
                self.assertEqual((8, 16), (wall['from'][1], wall['to'][1]))
                rotation = wall['rotation']
                angle = math.radians(rotation['angle'])
                ox, _, oz = rotation['origin']
                for x in (wall['from'][0], wall['to'][0]):
                    dx, dz = x - ox, wall['from'][2] - oz
                    point = (round(ox + dx * math.cos(angle) + dz * math.sin(angle), 6),
                             round(oz - dx * math.sin(angle) + dz * math.cos(angle), 6))
                    endpoints[point] += 1
            self.assertEqual(expected, set(endpoints))
            self.assertEqual({2}, set(endpoints.values()))


if __name__ == '__main__':
    unittest.main()
