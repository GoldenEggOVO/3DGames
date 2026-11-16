import json
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / 'src/main/resources/vanilla-models/tabletop'
TEXTURES = ROOT / 'resource-pack/assets/3dgames/textures/item'


class WarmTableThemeTest(unittest.TestCase):
    def test_native_shared_furniture_uses_brown_and_green(self):
        round_table = json.loads((MODELS / 'shared/round_table.json').read_text())['blocks']
        self.assertEqual({'BROWN_CONCRETE'}, {p['material'] for p in round_table[:36]})
        self.assertEqual({'GREEN_TERRACOTTA'}, {p['material'] for p in round_table[36:52]})
        square = json.loads((MODELS / 'shared/square_table.json').read_text())['blocks']
        self.assertEqual('BROWN_CONCRETE', square[0]['material'])
        self.assertTrue(all(p['material'] == 'BROWN_TERRACOTTA' for p in square[1:5]))

    def test_native_classic_frames_change_without_recoloring_board_parts(self):
        paths = list(MODELS.glob('*/table.json'))
        frames = 0
        for path in paths:
            blocks = json.loads(path.read_text())['blocks']
            rim = [p for p in blocks if p['name'].startswith(('rim_front_', 'rim_side_'))]
            if not rim:
                continue
            frames += 1
            self.assertTrue(all(p['material'] == 'BROWN_CONCRETE' for p in rim), str(path))
            self.assertEqual('BROWN_TERRACOTTA', next(p for p in blocks if p['name'] == 'wood_base')['material'])
        self.assertGreaterEqual(frames, 10)

    def test_packed_furniture_and_card_felt_use_the_same_palette(self):
        shell = Image.open(TEXTURES / 'shared/materials/future_shell.png').convert('RGBA').getpixel((0, 0))
        self.assertGreater(shell[0], shell[1])
        self.assertGreater(shell[1], shell[2])
        self.assertLess(shell[0], 150)
        for game in ('color_eight', 'doudizhu', 'liars_bar', 'texas_holdem'):
            with Image.open(TEXTURES / f'tabletop/{game}/{game}_table_top.png') as image:
                colors = image.convert('RGBA').getcolors(10000000)
            count, color = max((n, c) for n, c in colors if c[3] == 255)
            self.assertGreater(color[1], color[0], game)
            self.assertGreater(color[1], color[2], game)
            self.assertGreater(count, 10000, game)

    def test_mahjong_cloth_is_muted_in_both_renderers(self):
        table = json.loads((MODELS / 'mahjong/table.json').read_text())['blocks']
        felt = json.loads((MODELS / 'mahjong/surface.json').read_text())['blocks']
        self.assertEqual('GREEN_TERRACOTTA', next(p for p in table if p['name'] == 'part_9')['material'])
        self.assertEqual('GREEN_TERRACOTTA', felt[0]['material'])
        with Image.open(TEXTURES / 'tabletop/mahjong/mahjong_table_cloth.png') as image:
            r, g, b, _ = image.convert('RGBA').getpixel((512, 512))
        self.assertGreater(g, r)
        self.assertLess(g, 100)


if __name__ == '__main__':
    unittest.main()
