import unittest
import math

from tools.tests.test_established_model_geometry import NATIVE, PACKED, load
from tools.tests.test_table_corners import contains, shape


def interval(part):
    return [part['position'][1] - part['size'][1] / 2,
            part['position'][1] + part['size'][1] / 2]


class ModelLayerJoinsTest(unittest.TestCase):
    def assert_supported(self, upper, lower):
        self.assertLessEqual(upper[0], lower[1] + 1e-7, 'Air gap below the upper model layer')
        self.assertGreater(upper[1], lower[1], 'The visible upper surface must remain above its support')

    def test_all_native_chess_necks_join_their_base(self):
        for path in sorted((NATIVE / 'chess/pieces').glob('*.json')):
            with self.subTest(piece=path.stem):
                parts = {part['name']: part for part in load(path)['blocks']}
                base = [min(interval(parts[f'part_{i}'])[0] for i in range(3)),
                        max(interval(parts[f'part_{i}'])[1] for i in range(3))]
                self.assert_supported(interval(parts['part_3']), base)

    def test_native_draught_king_crown_joins_the_copper_band(self):
        for side in ('black', 'white'):
            with self.subTest(side=side):
                parts = {part['name']: part for part in load(NATIVE / f'draughts/{side}_king.json')['blocks']}
                self.assert_supported(interval(parts['part_6']), interval(parts['part_3']))

    def test_yacht_felt_and_tray_sides_join_the_base_in_both_modes(self):
        for filename, base, upper in (('dice_table', 'part_0', range(1, 6)),
                                      ('score_table', 'part_10', (11,))):
            parts = {part['name']: part for part in load(NATIVE / f'yacht/{filename}.json')['blocks']}
            for index in upper:
                with self.subTest(mode='native', table=filename, part=index):
                    self.assert_supported(interval(parts[f'part_{index}']), interval(parts[base]))
        parts = {part.get('name'): part for part in load(PACKED / 'yacht/yacht_table.json')['elements']}
        rim = [name for name in parts if name and name.startswith('table_rim_')]
        for base, upper in (('table_dice_body', ['dice_felt', *rim]), ('table_score_body', ['score_paper'])):
            for name in upper:
                with self.subTest(mode='packed', part=name):
                    self.assert_supported([parts[name]['from'][1], parts[name]['to'][1]],
                                          [parts[base]['from'][1], parts[base]['to'][1]])

    def test_mastermind_panel_sides_are_covered_without_coplanar_faces(self):
        parts = load(PACKED / 'mastermind/mastermind_board.json')['elements']
        base = next(part for part in parts if part.get('name') == 'plastic_panel_base')
        fields = [part for part in parts if part.get('name', '').startswith('plastic_field_')]
        foundation = next(part for part in parts if part.get('name') == 'plastic_foundation')
        side_rims = [part for part in parts if part.get('name', '').startswith('rim_side_')]
        supports = sorted([foundation, *side_rims], key=lambda part: part['from'][0])
        self.assertEqual(2, len(side_rims))
        covered_x = base['from'][0]
        for support in supports:
            self.assertLessEqual(support['from'][2], base['from'][2])
            self.assertGreaterEqual(support['to'][2], base['to'][2])
            self.assertLessEqual(support['from'][1], base['from'][1])
            self.assertGreaterEqual(support['to'][1], base['from'][1])
            self.assertLessEqual(support['from'][0], covered_x + 1e-7)
            covered_x = max(covered_x, support['to'][0])
        self.assertGreaterEqual(covered_x, base['to'][0], 'Foundation and rims cover the inset underside')
        for face, axis, high, along in (('north', 2, False, 0), ('south', 2, True, 0),
                                        ('west', 0, False, 2), ('east', 0, True, 2)):
            with self.subTest(face=face):
                edge = (max if high else min)(part['to' if high else 'from'][axis] for part in fields)
                target_start = min(part['from'][along] for part in fields)
                target_end = max(part['to'][along] for part in fields)
                self.assertGreater(base['from'][axis], min(part['from'][axis] for part in fields))
                self.assertLess(base['to'][axis], max(part['to'][axis] for part in fields))
                intervals = sorted((part['from'][along], part['to'][along]) for part in fields
                                   if face in part['faces']
                                   and abs(part['to' if high else 'from'][axis] - edge) < 1e-7
                                   and part['from'][1] <= foundation['to'][1] + 1e-7
                                   and part['to'][1] >= base['to'][1])
                covered = target_start
                for start, end in intervals:
                    if start > covered + 1e-7:
                        break
                    covered = max(covered, end)
                self.assertGreaterEqual(covered + 1e-7, target_end, 'Outside edge must remain covered')
                self.assertNotIn(face, base['faces'], 'A hidden face overlaps the visible edge')

    def test_native_mastermind_panel_has_no_shared_outer_faces(self):
        parts = load(NATIVE / 'mastermind/table.json')['blocks']
        base = next(part for part in parts if part.get('name') == 'plastic_panel_base')
        foundation = next(part for part in parts if part.get('name') == 'plastic_foundation')
        fields = [part for part in parts if part.get('name', '').startswith('plastic_field_')]
        bounds = lambda part, axis: (part['position'][axis] - part['size'][axis] / 2,
                                     part['position'][axis] + part['size'][axis] / 2)
        self.assertLess(bounds(base, 1)[0], bounds(foundation, 1)[1], 'The panel must meet its foundation')
        for field in fields:
            for axis in range(3):
                for side in range(2):
                    if abs(bounds(base, axis)[side] - bounds(field, axis)[side]) > 1e-6:
                        continue
                    other_axes = [a for a in range(3) if a != axis]
                    overlaps = [min(bounds(base, a)[1], bounds(field, a)[1])
                                - max(bounds(base, a)[0], bounds(field, a)[0]) for a in other_axes]
                    self.assertFalse(all(overlap > 1e-6 for overlap in overlaps),
                                     f"Coplanar panel face with {field['name']}")

    def test_yacht_tray_undersides_do_not_overlap_inside_the_base(self):
        parts = load(PACKED / 'yacht/yacht_table.json')['elements']
        body = [part for part in parts if part.get('name', '').startswith('table_dice_body')]
        for upper in (part for part in parts if part.get('name', '') == 'dice_felt' or part.get('name', '').startswith('table_rim_')):
            with self.subTest(part=upper['name']):
                self.assertLessEqual(upper['from'][1], max(part['to'][1] for part in body))
                self.assertGreaterEqual(upper['from'][1], min(part['to'][1] for part in body) - .004)
                self.assertNotIn('down', upper['faces'], 'The support hides this overlapping underside')
                center, size, angle = shape(upper, True)
                y = (body[0]['from'][1] + body[0]['to'][1]) / 2
                for sx in (-.49, .49):
                    for sz in (-.49, .49):
                        x,z = size[0]*sx, size[2]*sz
                        point = (center[0]+x*math.cos(angle)+z*math.sin(angle), y,
                                 center[2]-x*math.sin(angle)+z*math.cos(angle))
                        self.assertTrue(any(contains(part, point, True) for part in body), 'Tray rim has no support below it')

    def test_packed_connect_four_feet_rest_on_the_table_shell(self):
        model = load(PACKED / 'connect_four/connect_four_board.json')
        shell = next(part for part in model['elements']
                     if part.get('name') == 'table_body')
        feet = [part for part in model['elements']
                if not part.get('rotation') and part['from'][1] < 8 < part['to'][1]
                and part['to'][0] - part['from'][0] > 2
                and {face['texture'] for face in part['faces'].values()} == {'#POLISHED_DEEPSLATE'}]
        self.assertEqual(2, len(feet))
        for foot in feet:
            with self.subTest(position=foot['from'][0]):
                self.assertLessEqual(foot['from'][1], shell['to'][1] + 1e-7,
                                     'The rack foot must meet its table shell')
                self.assertAlmostEqual(8.48, foot['to'][1])


if __name__ == '__main__':
    unittest.main()
