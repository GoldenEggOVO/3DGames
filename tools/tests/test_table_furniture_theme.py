import json
import unittest
from pathlib import Path
from PIL import Image


ROOT = Path(__file__).resolve().parents[2] / 'resource-pack/assets/3dgames/models/item/tabletop'
CLASSICS = ('2048', 'backgammon', 'battleship', 'dots_and_boxes', 'hex', 'klondike', 'mancala',
            'mastermind', 'memory', 'mini_shogi', 'monopoly', 'nine_mens_morris', 'shogi')
LEGACY = ('checkers/checkers_board', 'chess/chess_board', 'draughts/draughts_board',
          'go/go_board_9x9', 'go/go_board_13x13', 'go/go_board_19x19', 'gomoku/gomoku_board',
          'xiangqi/xiangqi_board', 'reversi/reversi_board', 'ludo/ludo_board',
          'connect_four/connect_four_board', 'color_eight/color_eight_table', 'doudizhu/doudizhu_table',
          'liars_bar/liars_bar_table', 'texas_holdem/texas_holdem_table', 'mahjong/mahjong_table',
          'ludo/ludo_dice_tray', 'yacht/yacht_table')


class TableFurnitureThemeTest(unittest.TestCase):
    def test_every_table_body_uses_the_same_frame_and_chassis_materials(self):
        names = [f'{kind}/{kind}_board' for kind in CLASSICS] + list(LEGACY)
        for name in names:
            with self.subTest(model=name):
                model = json.loads((ROOT / (name + '.json')).read_text(encoding='utf-8'))
                used = {model['textures'][face['texture'][1:]]
                        for part in model['elements'] for face in part['faces'].values()}
                for role in ('shell', 'chassis'):
                    self.assertIn('3dgames:item/shared/materials/future_' + role, used, name)
                has_edge_accents = name in ('yacht/yacht_table', 'color_eight/color_eight_table',
                                            'doudizhu/doudizhu_table', 'liars_bar/liars_bar_table',
                                            'texas_holdem/texas_holdem_table')
                self.assertEqual(has_edge_accents, '3dgames:item/shared/materials/future_accent' in used)

    def test_round_table_rims_cover_the_old_edge_without_adding_slice_geometry(self):
        for kind in ('color_eight', 'doudizhu', 'liars_bar', 'texas_holdem'):
            with self.subTest(model=kind):
                model = json.loads((ROOT / kind / (kind + '_table.json')).read_text())
                self.assertLessEqual(sum(len(part['faces']) for part in model['elements']), 150)
                rim = next(part for part in model['elements'] if part.get('name') == 'future_rim')
                texture = model['textures'][rim['faces']['up']['texture'][1:]]
                path = ROOT.parents[2] / 'textures' / (texture.split(':')[1] + '.png')
                with Image.open(path) as image:
                    self.assertEqual((102, 67, 42, 255), image.getpixel((128, 253)))
                    self.assertEqual(0, image.getpixel((128, 128))[3], 'Keep original felt and markings visible')
                    self.assertEqual(0, image.getpixel((0, 0))[3], 'Keep the circular silhouette')

    def test_yacht_keep_slots_retain_their_functional_material(self):
        root = Path(__file__).resolve().parents[2]
        model = json.loads((root / 'src/main/resources/vanilla-models/tabletop/yacht/dice_table.json').read_text())
        slots = [part for part in model['blocks'] if part['name'] in
                 ('part_36', 'part_38', 'part_40', 'part_42', 'part_44')]
        self.assertEqual(5, len(slots))
        self.assertTrue(all(part['material'] == 'WAXED_EXPOSED_COPPER' and 'brightness' not in part for part in slots))


if __name__ == '__main__':
    unittest.main()
