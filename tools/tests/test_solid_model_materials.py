import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2] / 'resource-pack/assets/3dgames/textures/item'
MATERIALS = (
    'shared/materials/wood.png',
    'shared/materials/slate_surface.png',
    'shared/materials/material_blue_concrete.png',
    'shared/materials/future_shell.png',
    'shared/materials/future_chassis.png',
    'shared/materials/future_accent.png',
    'shared/table_parts/future_board_surface_chassis.png',
    'shared/table_parts/future_pedestal_chassis.png',
    'shared/table_parts/future_round_table_chassis.png',
    'shared/table_parts/future_round_table_rim_shell.png',
    'tabletop/connect_four/connect_four_board_faces.png',
    'tabletop/connect_four/connect_four_support_end_caps.png',
    'tabletop/mahjong/mahjong_control_panel_bottom.png',
    'tabletop/yacht/yacht_dice_tray_felt.png',
)


class SolidModelMaterialsTest(unittest.TestCase):
    def test_furniture_uses_solid_colors_without_wood_grain_or_surface_waves(self):
        for name in MATERIALS:
            with self.subTest(texture=name):
                image = Image.open(ROOT / name).convert('RGBA')
                colors = {(r, g, b) for r, g, b, a in image.get_flattened_data() if a > 0}
                self.assertEqual(1, len(colors), 'Furniture surfaces must use a solid color: ' + name)


if __name__ == '__main__':
    unittest.main()
