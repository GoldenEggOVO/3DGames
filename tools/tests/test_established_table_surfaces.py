import json
import unittest
from collections import Counter
from pathlib import Path

from tools.tests.test_established_model_geometry import rotated


ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'src/main/resources/vanilla-models/tabletop'
PACKED = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop'
ROUND_TABLES = [('color_eight', 131, 82), ('doudizhu', 131, 82),
                ('liars_bar', 131, 82), ('texas_holdem', 147, 98)]


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def native_top(part):
    return part['position'][1] + part['size'][1] / 2


def native_bottom(part):
    return part['position'][1] - part['size'][1] / 2


def world(value):
    return (value - 8) / 16


def contour_degrees(parts):
    degrees = Counter()
    for part in parts:
        if part.get('rotation', {}).get('axis') != 'y' or len(part['faces']) != 1:
            continue
        for y in [part['from'][1], part['to'][1]]:
            for x, z in [(part['from'][0], part['from'][2]), (part['to'][0], part['to'][2])]:
                point = rotated([x, y, z], part['rotation'])
                section = (round(part['from'][1], 7), round(part['to'][1], 7))
                degrees[section + tuple(round(v, 7) for v in point)] += 1
    return degrees


class EstablishedTableSurfacesTest(unittest.TestCase):
    def test_round_playing_surfaces_and_trim_match_native_support_heights(self):
        native = load(NATIVE / 'shared/round_table.json')['blocks']
        felt = max(native_top(part) for part in native if part['material'] == 'GREEN_TERRACOTTA')
        rim = min(native_top(part) for part in native if part['material'] == 'BROWN_CONCRETE')
        for kind, rim_index, _ in ROUND_TABLES:
            parts = load(PACKED / kind / (kind + '_table.json'))['elements']
            with self.subTest(game=kind, layer='felt'):
                self.assertAlmostEqual(felt, world(parts[0]['to'][1]), places=8,
                                       msg='Cards need the same supporting felt in both modes')
                self.assertTrue(all(part['to'][1] == parts[0]['to'][1] for part in parts[1:49]),
                                'The raised felt cap must still meet every outer wall')
            with self.subTest(game=kind, layer='rim'):
                self.assertAlmostEqual(rim, world(parts[rim_index]['from'][1]), places=8)
                self.assertAlmostEqual(rim, world(parts[rim_index]['to'][1]), places=8)

    def test_round_support_columns_and_feet_still_meet_the_original_floor(self):
        native = load(NATIVE / 'shared/round_table.json')['blocks']
        floor = min(native_bottom(part) for part in native)
        foot_top = native_top(next(part for part in native if part['name'] == 'part_53'))
        for kind, rim_index, foot_index in ROUND_TABLES:
            with self.subTest(game=kind):
                parts = load(PACKED / kind / (kind + '_table.json'))['elements']
                self.assertAlmostEqual(floor, world(parts[foot_index]['from'][1]), places=8)
                self.assertAlmostEqual(foot_top, world(parts[foot_index]['to'][1]), places=8)
                self.assertAlmostEqual(parts[foot_index]['to'][1], parts[49]['from'][1], places=8)
                self.assertAlmostEqual(parts[0]['from'][1], parts[49]['to'][1], places=8)
                self.assertLessEqual(len(parts), rim_index + 1)

    def test_all_round_table_wall_contours_close_at_both_height_endpoints(self):
        for kind, _, _ in ROUND_TABLES:
            with self.subTest(game=kind):
                parts = load(PACKED / kind / (kind + '_table.json'))['elements']
                degrees = contour_degrees(parts)
                self.assertTrue(degrees)
                self.assertEqual({2}, set(degrees.values()), 'No open or duplicate perimeter wall endpoints')

    def test_mahjong_felt_and_rim_follow_native_support_heights(self):
        table = load(NATIVE / 'mahjong/table.json')['blocks']
        felt = max(native_top(part) for part in load(NATIVE / 'mahjong/surface.json')['blocks'])
        rim = native_top(next(part for part in table if part['name'] == 'part_6'))
        packed = {part.get('name'): part for part in load(PACKED / 'mahjong/mahjong_table.json')['elements']}
        with self.subTest(layer='felt'):
            self.assertAlmostEqual(felt, world(packed['table_playing_surface']['to'][1]), places=8)
        with self.subTest(layer='rim'):
            self.assertAlmostEqual(rim, world(packed['table_rim_north']['to'][1]), places=8)
            self.assertEqual(4, len([name for name in packed if name.startswith('table_rim_bevel_')]))

    def test_mahjong_felt_meets_inner_walls_without_raising_the_outer_base_walls(self):
        packed = {part.get('name'): part for part in load(PACKED / 'mahjong/mahjong_table.json')['elements']}
        base, felt, rim = (packed[name] for name in ('table_body', 'table_playing_surface', 'table_rim_north'))
        self.assertEqual(8, base['to'][1], 'Keep the existing outer base/rim wall joint')
        self.assertEqual(base['to'][1], rim['from'][1])
        self.assertLess(rim['from'][1], felt['to'][1])
        self.assertLess(felt['to'][1], rim['to'][1])
        for name, part in packed.items():
            if name.startswith('table_rim_'):
                self.assertLessEqual(part['from'][1], base['to'][1])
                self.assertGreaterEqual(part['to'][1], rim['to'][1])

    def test_mahjong_legs_remain_on_the_native_floor_under_the_same_base(self):
        native = load(NATIVE / 'mahjong/table.json')['blocks']
        floor = min(native_bottom(part) for part in native)
        packed = load(PACKED / 'mahjong/mahjong_table.json')['elements']
        legs = [part for part in packed if part.get('name', '').startswith('table_leg_')]
        self.assertEqual(4, len(legs))
        base = next(part for part in packed if part.get('name') == 'table_body')
        for leg in legs:
            self.assertAlmostEqual(floor, world(leg['from'][1]), places=8)
            self.assertEqual(base['from'][1], leg['to'][1])
        self.assertLessEqual(len(packed), 28)


if __name__ == '__main__':
    unittest.main()
