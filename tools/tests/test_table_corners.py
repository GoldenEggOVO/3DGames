import json
import math
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'src/main/resources/vanilla-models/tabletop'
PACKED = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop'
CLASSICS = ('2048', 'backgammon', 'battleship', 'dots_and_boxes', 'hex', 'klondike',
            'mancala', 'mastermind', 'memory', 'mini_shogi', 'monopoly', 'nine_mens_morris', 'shogi')
LEGACY = ('checkers/checkers_board', 'chess/chess_board', 'draughts/draughts_board',
          'go/go_board_9x9', 'go/go_board_13x13', 'go/go_board_19x19', 'gomoku/gomoku_board',
          'xiangqi/xiangqi_board', 'reversi/reversi_board', 'ludo/ludo_board', 'connect_four/connect_four_board')
NATIVE_CASES = [(f'{kind}/table', 'wood_base') for kind in CLASSICS] + [
    ('shared/square_table', 'part_0'), ('mahjong/table', 'part_0'),
    ('yacht/dice_table', 'part_0'), ('yacht/score_table', 'part_10'),
    ('ludo/dice_tray_large', 'part_0'), ('ludo/dice_tray_compact', 'part_0')]
PACKED_CASES = [(f'{kind}/{kind}_board', 'wood_base') for kind in CLASSICS] + [
    (name, 'table_body') for name in LEGACY] + [
    ('mahjong/mahjong_table', 'table_body'), ('yacht/yacht_table', 'table_dice_body'),
    ('yacht/yacht_table', 'table_score_body'), ('ludo/ludo_dice_tray', 'table_body')]


def shape(part, packed):
    if packed:
        center = [(a + b) / 2 for a, b in zip(part['from'], part['to'])]
        size = [b - a for a, b in zip(part['from'], part['to'])]
        rotation = part.get('rotation', {}).get('angle', 0)
    else:
        center, size = part['position'], part['size']
        rotation = part.get('rotation', [0, 0, 0])[1]
    return center, size, math.radians(rotation)


def contains(part, point, packed):
    center, size, angle = shape(part, packed)
    x, y, z = [a - b for a, b in zip(point, center)]
    x, z = x * math.cos(angle) - z * math.sin(angle), x * math.sin(angle) + z * math.cos(angle)
    return all(abs(value) <= span / 2 + 1e-8 for value, span in zip((x, y, z), size))


class TableCornersTest(unittest.TestCase):
    def check_corners(self, root, cases, packed):
        for name, body in cases:
            with self.subTest(model=name, body=body):
                model = json.loads((root / (name + '.json')).read_text(encoding='utf-8'))
                parts = [part for part in model['elements' if packed else 'blocks']
                         if part.get('name', '') == body or part.get('name', '').startswith(body + '__bevel_')]
                self.assertTrue(parts, 'Table body must be identifiable')
                vertices = []
                for part in parts:
                    center, size, angle = shape(part, packed)
                    for x in (-size[0] / 2, size[0] / 2):
                        for z in (-size[2] / 2, size[2] / 2):
                            vertices.append((center[0] + x * math.cos(angle) + z * math.sin(angle),
                                             center[2] - x * math.sin(angle) + z * math.cos(angle)))
                low_x, high_x = min(p[0] for p in vertices), max(p[0] for p in vertices)
                low_z, high_z = min(p[1] for p in vertices), max(p[1] for p in vertices)
                if packed and name in LEGACY:
                    expected = 18 if name.startswith('connect_four/') else 36
                    self.assertAlmostEqual(expected, high_x-low_x, places=7)
                    self.assertAlmostEqual(expected, high_z-low_z, places=7)
                center, size, _ = shape(parts[0], packed)
                y = center[1]
                cut = min(high_x - low_x, high_z - low_z) * .03
                for side_x, edge_x in ((1, low_x), (-1, high_x)):
                    for side_z, edge_z in ((1, low_z), (-1, high_z)):
                        for a, b, expected in ((.1, .1, False), (.4, .8, True), (.8, .4, True), (1.1, 1.1, True)):
                            point = (edge_x + side_x * cut * a, y, edge_z + side_z * cut * b)
                            self.assertEqual(expected, any(contains(part, point, packed) for part in parts),
                                             f'{name}: corner coverage at {a}, {b}')
                        for i in range(11):
                            a = .015 + i * .1
                            b = 1.03 - a
                            point = (edge_x + side_x * cut * a, y, edge_z + side_z * cut * b)
                            self.assertTrue(any(contains(part, point, packed) for part in parts), 'Chamfer edge remains closed')
                for point in (((low_x + high_x) / 2, y, low_z + cut * .1),
                              ((low_x + high_x) / 2, y, high_z - cut * .1),
                              (low_x + cut * .1, y, (low_z + high_z) / 2),
                              (high_x - cut * .1, y, (low_z + high_z) / 2)):
                    self.assertTrue(any(contains(part, point, packed) for part in parts), 'Straight edges remain filled')

    def test_every_native_rectangular_table_has_the_same_small_corner_cut(self):
        self.check_corners(NATIVE, NATIVE_CASES, False)

    def test_every_packed_rectangular_table_has_the_same_small_corner_cut(self):
        self.check_corners(PACKED, PACKED_CASES, True)

    def test_flush_rims_meet_their_support_without_overlapping_side_faces(self):
        for packed in (False, True):
            root = PACKED if packed else NATIVE
            cases = [(f'{kind}/{kind}_board' if packed else f'{kind}/table',
                      'wood_base', ('rim_side_-1', 'rim_side_1')) for kind in CLASSICS]
            if packed:
                cases += [(name, body, ('table_rim_west', 'table_rim_east')) for name, body in (
                    ('mahjong/mahjong_table', 'table_body'), ('yacht/yacht_table', 'table_dice_body'),
                    ('ludo/ludo_dice_tray', 'table_body'))]
            else:
                cases += [(name, 'part_0', ('part_2', 'part_4')) for name in (
                    'yacht/dice_table', 'ludo/dice_tray_large', 'ludo/dice_tray_compact')]
            for name, body, sides in cases:
                model = json.loads((root / (name + '.json')).read_text(encoding='utf-8'))
                parts = {p['name']: p for p in model['elements' if packed else 'blocks'] if 'name' in p}
                joints = [(body, side) for side in sides]
                for x in (-1, 1):
                    for z in (-1, 1):
                        joints.append((f'{body}__bevel_corner_{x}_{z}', f'table_rim_bevel_{x}_{z}'))
                for lower, upper in joints:
                    with self.subTest(packed=packed, model=name, joint=upper):
                        base, base_size, _ = shape(parts[lower], packed)
                        rim, rim_size, _ = shape(parts[upper], packed)
                        self.assertAlmostEqual(base[1] + base_size[1] / 2, rim[1] - rim_size[1] / 2, places=7)

    def test_mahjong_cloth_keeps_its_mapping_inside_the_chamfered_frame(self):
        model = json.loads((PACKED / 'mahjong/mahjong_table.json').read_text(encoding='utf-8'))
        cloth = next(p for p in model['elements'] if p.get('name') == 'table_playing_surface')
        for side in ('from', 'to'):
            for axis in (0, 2):
                self.assertLessEqual(abs(cloth[side][axis] - 8), 22.5)
        uv = cloth['faces']['up']['uv']
        self.assertAlmostEqual((cloth['from'][0] + 16) / 3, uv[0])
        self.assertAlmostEqual((cloth['to'][0] + 16) / 3, uv[2])
        self.assertLessEqual(cloth['from'][0], -14.4, 'Cloth still covers the visible inner table')
        self.assertGreaterEqual(cloth['to'][0], 30.4)


if __name__ == '__main__':
    unittest.main()
