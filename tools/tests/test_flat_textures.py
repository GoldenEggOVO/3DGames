from pathlib import Path
import unittest

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / 'resource-pack/assets/3dgames/textures/item'


def colors_in_family(path, predicate):
    with Image.open(ROOT / path) as image:
        return {(r, g, b) for r, g, b, a in image.convert('RGBA').get_flattened_data()
                if a and predicate(r, g, b)}


class FlatTextureTest(unittest.TestCase):
    def test_card_table_wood_rims_and_felt_are_flat(self):
        for game in ('color_eight', 'doudizhu', 'liars_bar', 'texas_holdem'):
            path = f'tabletop/{game}/{game}_table_top.png'
            with self.subTest(texture=path):
                rim = colors_in_family(path, lambda r, g, b: r-g == 8 and g-b == 7 and 57 <= r <= 73)
                self.assertEqual({(65, 57, 50)}, rim)
                felt = colors_in_family(path, lambda r, g, b: r-g == -30 and g-b == 24 and 51 <= r <= 55)
                self.assertEqual({(53, 83, 59)}, felt)

    def test_control_panel_background_is_flat(self):
        panel = colors_in_family('tabletop/mahjong/mahjong_control_panel_top.png',
                                 lambda r, g, b: r-g == 19 and g-b == 12 and 58 <= r <= 74)
        self.assertEqual({(66, 47, 35)}, panel)

    def test_pale_materials_have_no_repeating_shading(self):
        paths = list((ROOT / 'tabletop/mahjong').glob('*_face.png'))
        paths += list((ROOT / 'shared/dice').glob('dice_face_*.png'))
        paths += [ROOT / 'shared/materials/ivory.png']
        self.assertGreaterEqual(len(paths), 40)
        for path in paths:
            with self.subTest(texture=path.name):
                family = colors_in_family(path.relative_to(ROOT),
                                          lambda r, g, b: r-g == 9 and g-b == 19 and 242 <= r <= 246)
                self.assertEqual({(244, 235, 216)}, family)

class SharedMaterialFlatnessTest(unittest.TestCase):
    def test_all_shared_model_materials_use_single_solid_color(self):
        materials = list((ROOT / 'shared/materials').glob('*.png'))
        self.assertTrue(materials)
        for path in materials:
            with self.subTest(texture=path.name), Image.open(path) as image:
                colors = {(r, g, b) for r, g, b, a in image.convert('RGBA').get_flattened_data() if a}
                self.assertEqual(1, len(colors))


if __name__ == '__main__':
    unittest.main()
