import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / 'src/main/resources/vanilla-models/tabletop'
MAP_PLANE = -.023359375


def model(name):
    return json.loads((MODELS / (name + '.json')).read_text(encoding='utf-8'))['blocks']


def bounds(part):
    return [(center - size / 2, center + size / 2)
            for center, size in zip(part['position'], part['size'])]


class NativeSquareSupportTest(unittest.TestCase):
    def test_square_support_meets_the_real_invisible_map_plane_with_clearance(self):
        parts = model('shared/square_table')
        base = bounds(parts[0])
        self.assertAlmostEqual(MAP_PLANE - .0015, base[1][1], places=8)
        self.assertAlmostEqual(-.19, base[1][0], places=8)
        self.assertEqual(11, len(parts))
        shell = [part for part in parts if part['name'] == 'part_0' or part['name'].startswith('part_0__bevel_')]
        self.assertEqual(7, len(shell))
        self.assertTrue(all(bounds(part)[1][1] < MAP_PLANE - .001 for part in shell),
                        'Chamfered corners must stay below the invisible map plane')

    def test_square_legs_keep_the_original_dimensions_and_floor(self):
        for leg in model('shared/square_table')[1:5]:
            self.assertEqual([.15, .90125, .15], leg['size'])
            self.assertEqual(-.580625, leg['position'][1])
            self.assertAlmostEqual(-1.03125, bounds(leg)[1][0], places=8)

    def test_connect_four_copper_feet_rest_on_the_raised_square_support(self):
        top = bounds(model('shared/square_table')[0])[1][1]
        rack = model('connect_four/rack')
        for index in [16, 18]:
            with self.subTest(foot=index):
                foot = bounds(rack[index])
                self.assertAlmostEqual(top, foot[1][0], places=8)
                self.assertAlmostEqual(.04, foot[1][1] - foot[1][0], places=8)
                self.assertLess(foot[1][1], bounds(rack[8])[1][0])
                self.assertLess(foot[1][1], .05, 'Resting disc bottoms must stay clear of the feet')

    def test_connect_four_upright_bottoms_are_hidden_inside_the_lower_crossbar(self):
        rack = model('connect_four/rack')
        bar = bounds(rack[8])
        for part in rack[:8]:
            upright = bounds(part)
            self.assertGreater(upright[1][0], bar[1][0] + .001)
            self.assertLess(upright[1][0], bar[1][1])
            self.assertAlmostEqual(1.74, upright[1][1], places=8)
            for axis in [0, 2]:
                self.assertGreaterEqual(upright[axis][0], bar[axis][0])
                self.assertLessEqual(upright[axis][1], bar[axis][1])

    def test_connect_four_cap_has_a_small_overhang_above_the_posts(self):
        rack = model('connect_four/rack')
        cap = bounds(rack[19])
        self.assertAlmostEqual(.002, bounds(rack[15])[0][0] - cap[0][0], places=8)
        self.assertAlmostEqual(.002, cap[0][1] - bounds(rack[17])[0][1], places=8)
        self.assertEqual(20, len(rack))

    def test_connect_four_side_posts_join_the_grid_without_sharing_its_front_planes(self):
        rack = model('connect_four/rack')
        left, right = bounds(rack[15]), bounds(rack[17])
        self.assertAlmostEqual(bounds(rack[0])[0][0], left[0][1], places=8,
                               msg='The left grid edge must join the side post')
        self.assertAlmostEqual(bounds(rack[7])[0][1], right[0][0], places=8,
                               msg='The right grid edge must join the side post')
        self.assertAlmostEqual(-1.125, left[0][0], places=8)
        self.assertAlmostEqual(1.125, right[0][1], places=8)
        for index in [15, 17]:
            self.assertGreater(rack[index]['size'][2], rack[8]['size'][2] + .0009)
            self.assertLess(rack[index]['size'][2], rack[19]['size'][2])

    def test_connect_four_post_sides_do_not_share_the_square_shell_side_plane(self):
        top = bounds(model('shared/square_table')[0])[1][1]
        rack = model('connect_four/rack')
        for post_index, foot_index in [(15, 16), (17, 18)]:
            post, foot = bounds(rack[post_index]), bounds(rack[foot_index])
            self.assertGreater(post[1][0], top + .0005,
                               'Post bottoms must clear the shell side and stay inside the copper feet')
            self.assertLess(post[1][0], foot[1][1])
            self.assertAlmostEqual(1.79, post[1][1], places=8)


if __name__ == '__main__':
    unittest.main()
