import json
import math
import unittest
from collections import Counter
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
PACKED = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop'
NATIVE = ROOT / 'src/main/resources/vanilla-models/tabletop'


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def rotated(point, rotation):
    x, y, z = [point[i] - rotation['origin'][i] for i in range(3)]
    angle = math.radians(rotation['angle'])
    cosine, sine = math.cos(angle), math.sin(angle)
    if rotation['axis'] == 'y':
        point = [x * cosine + z * sine, y, -x * sine + z * cosine]
    elif rotation['axis'] == 'x':
        point = [x, y * cosine - z * sine, y * sine + z * cosine]
    else:
        point = [x * cosine - y * sine, x * sine + y * cosine, z]
    return [point[i] + rotation['origin'][i] for i in range(3)]


def overlap(a, b):
    return [min(a['to'][i], b['to'][i]) - max(a['from'][i], b['from'][i])
            for i in range(3)]


class EstablishedModelGeometryTest(unittest.TestCase):
    def test_texas_table_supports_every_near_control_corner(self):
        model = load(PACKED / 'texas_holdem/texas_holdem_table.json')
        cap = model['elements'][0]
        texture = model['textures'][cap['faces']['up']['texture'][1:]]
        path = ROOT / 'resource-pack/assets' / (texture.replace(':', '/textures/') + '.png')
        outside = []
        with Image.open(path) as image:
            for capacity in range(2, 7):
                for seat in range(capacity):
                    angle = 2 * math.pi * seat / capacity
                    for center in [-.48, -.16, .16, .48]:
                        for tangent in [center - .145, center + .145]:
                            for radius in [1.185, 1.355]:
                                x = tangent * math.cos(angle) + radius * math.sin(angle)
                                z = -tangent * math.sin(angle) + radius * math.cos(angle)
                                u = (8 + x * 16 - cap['from'][0]) / (cap['to'][0] - cap['from'][0])
                                v = (8 + z * 16 - cap['from'][2]) / (cap['to'][2] - cap['from'][2])
                                if not 0 <= u < 1 or not 0 <= v < 1:
                                    outside.append((capacity, seat, round(x, 4), round(z, 4)))
                                elif image.getpixel((int(u * image.width), int(v * image.height)))[3] == 0:
                                    outside.append((capacity, seat, round(x, 4), round(z, 4)))
        self.assertEqual([], outside[:8], 'Control corners lack table support')

    def test_texas_shell_wall_endpoints_all_form_closed_contours(self):
        model = load(PACKED / 'texas_holdem/texas_holdem_table.json')
        endpoints = Counter()
        for part in model['elements']:
            if part.get('rotation', {}).get('axis') != 'y' or len(part['faces']) != 1:
                continue
            a, b = part['from'][:], part['to'][:]
            b[1] = a[1]
            for point in (a, b):
                endpoints[tuple(round(v, 7) for v in rotated(point, part['rotation']))] += 1
        self.assertTrue(endpoints)
        self.assertEqual({2}, set(endpoints.values()), 'Every sidewall contour must stay closed')

    def test_yacht_grid_crossings_use_separate_top_planes_in_both_modes(self):
        native = load(NATIVE / 'yacht/score_table.json')['blocks']
        vertical = next(part for part in native if part['name'] == 'part_32')
        top = vertical['position'][1] + vertical['size'][1] / 2
        with self.subTest(mode='native'):
            for part in native:
                if part['name'] in [f'part_{i}' for i in range(16, 32)]:
                    self.assertGreater(abs(top - part['position'][1] - part['size'][1] / 2), .0002,
                                       part['name'] + ' overlaps the vertical grid top')
        packed = {part.get('name'): part for part in load(PACKED / 'yacht/yacht_table.json')['elements']}
        with self.subTest(mode='packed'):
            for index in range(16):
                part = packed[f'score_grid_{index}']
                self.assertGreater(abs(packed['score_grid_16']['to'][1] - part['to'][1]) / 8, .0002,
                                   'Packed Yacht grid must use the same separated world planes')

    def test_packed_king_crosses_and_knight_heads_have_no_solid_intersections(self):
        for side in ['black', 'white']:
            for piece, start, budget in [('king', 84, 87), ('knight', 67, 70)]:
                with self.subTest(side=side, piece=piece):
                    parts = load(PACKED / f'chess/chess_{side}_{piece}.json')['elements']
                    self.assertLessEqual(len(parts), budget)
                    for i, a in enumerate(parts[start:]):
                        for b in parts[start + i + 1:]:
                            if 'rotation' not in a and 'rotation' not in b:
                                self.assertTrue(any(length <= 1e-8 for length in overlap(a, b)),
                                                f'{piece} contains intersecting solid parts: {a} / {b}')

    def test_dead_stone_crosses_use_adjoining_arms(self):
        for side in ['black', 'white']:
            with self.subTest(side=side):
                parts = load(PACKED / f'go/stone_{side}_dead.json')['elements']
                self.assertLessEqual(len(parts), 36)
                for i, a in enumerate(parts[33:]):
                    for b in parts[34 + i:]:
                        self.assertTrue(any(length <= 1e-8 for length in overlap(a, b)),
                                        'Dead-stone cross arms must meet without overlapping top faces')

    def test_draught_king_copper_bottoms_stay_hidden_inside_the_base(self):
        for side in ['black', 'white']:
            with self.subTest(side=side):
                parts = load(NATIVE / f'draughts/{side}_king.json')['blocks']
                base = parts[:3]
                for copper in parts[3:6]:
                    x, y, z = copper['position']
                    width, height, depth = copper['size']
                    bottom = y - height / 2
                    self.assertAlmostEqual(.30, y + height / 2, places=8,
                                           msg='The visible copper band must retain its top')
                    for ix in range(5):
                        for iz in range(5):
                            px = x + (ix / 4 - .5) * width
                            pz = z + (iz / 4 - .5) * depth
                            hidden = any(abs(px - b['position'][0]) <= b['size'][0] / 2 + 1e-8
                                         and abs(pz - b['position'][2]) <= b['size'][2] / 2 + 1e-8
                                         and b['position'][1] - b['size'][1] / 2 < bottom - .0001
                                         < b['position'][1] + b['size'][1] / 2 for b in base)
                            self.assertTrue(hidden, 'Copper undersides must be covered by the base volume')

    def test_connect_four_top_cap_covers_supports_without_shared_front_planes(self):
        parts = {part.get('name'): part for part in load(PACKED / 'connect_four/connect_four_board.json')['elements']}
        cap = parts['rack_top_cap']
        for name in ['rack_support_left_front', 'rack_support_left_back', 'rack_support_right_front', 'rack_support_right_back']:
            part = parts[name]
            points = [rotated(point, part['rotation']) for point in [part['from'], part['to']]]
            self.assertTrue(all(cap['from'][2] + .001 < point[2] < cap['to'][2] - .001 for point in points),
                            f'Cap and support {name} must have different exterior depth planes')


if __name__ == '__main__':
    unittest.main()
