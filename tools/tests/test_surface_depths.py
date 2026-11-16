import json
import unittest
from pathlib import Path

from tools.tests import test_classic_models as geometry_checks


ROOT = Path(__file__).resolve().parents[2]


class SurfaceDepthsTest(unittest.TestCase):
    def test_round_table_column_ends_inside_the_foot_instead_of_on_its_bottom_face(self):
        path = ROOT / 'src/main/resources/vanilla-models/tabletop/shared/round_table.json'
        parts = json.loads(path.read_text(encoding='utf-8'))['blocks']
        column, foot = parts[-2:]
        column_bottom = column['position'][1] - column['size'][1] / 2
        foot_bottom = foot['position'][1] - foot['size'][1] / 2
        foot_top = foot['position'][1] + foot['size'][1] / 2
        self.assertGreater(column_bottom, foot_bottom + .001,
                           'The two downward faces must not share the exposed underside')
        self.assertLess(column_bottom, foot_top - .001, 'The column must stay joined to the foot')
        self.assertAlmostEqual(.005, column['position'][1] + column['size'][1] / 2)
        self.assertAlmostEqual(-1.03125, foot_bottom)
        self.assertEqual(54, len(parts))

    def test_intersecting_turn_ring_segments_keep_distinct_surface_planes(self):
        for direction in ('clockwise', 'counterclockwise'):
            path = ROOT / 'src/main/resources/vanilla-models/tabletop/color_eight' / ('turn_ring_' + direction + '.json')
            parts = json.loads(path.read_text(encoding='utf-8'))['blocks']
            self.assertEqual(24, len(parts), 'Reuse the existing native segments')
            for index, first in enumerate(parts):
                self.assertLessEqual(first['position'][1] + first['size'][1] / 2, .024)
                for second in parts[index + 1:]:
                    if geometry_checks.ClassicModelsTest.overlaps(geometry_checks.ClassicModelsTest.footprint(first), geometry_checks.ClassicModelsTest.footprint(second)):
                        top_one = first['position'][1] + first['size'][1] / 2
                        top_two = second['position'][1] + second['size'][1] / 2
                        self.assertGreaterEqual(abs(top_one - top_two), .00049,
                                                direction + ': ' + first['name'] + ' / ' + second['name'])


if __name__ == '__main__':
    unittest.main()
