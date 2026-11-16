import json
import unittest
from functools import lru_cache
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'src/main/resources/vanilla-models/tabletop'
PACKED = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop'
CLASSICS = ('2048', 'mancala', 'nine_mens_morris', 'dots_and_boxes', 'mastermind', 'hex',
            'memory', 'backgammon', 'battleship', 'klondike', 'shogi', 'mini_shogi', 'monopoly')
BOARDS = {'chess': 'chess/chess_board', 'xiangqi': 'xiangqi/xiangqi_board',
          'gomoku': 'gomoku/gomoku_board', 'go': 'go/go_board_19x19', 'go9': 'go/go_board_9x9',
          'go13': 'go/go_board_13x13', 'reversi': 'reversi/reversi_board',
          'draughts': 'draughts/draughts_board', 'checkers': 'checkers/checkers_board',
          'ludo': 'ludo/ludo_board', 'connectfour': 'connect_four/connect_four_board'}
ROUNDS = ('color_eight', 'doudizhu', 'liars_bar', 'texas_holdem')


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def native_supports():
    supports = {}
    for game in BOARDS:
        supports[game] = load(NATIVE / 'shared/square_table.json')['blocks'][1:5]
    for game in ROUNDS:
        supports[game] = load(NATIVE / 'shared/round_table.json')['blocks'][52:54]
    supports['mahjong'] = load(NATIVE / 'mahjong/table.json')['blocks'][1:5]
    supports['yacht'] = [part for name in ['dice_table', 'score_table']
                         for part in load(NATIVE / f'yacht/{name}.json')['blocks']
                         if part['name'] in [f'part_{i}' for i in [6, 7, 8, 9, 12, 13, 14, 15]]]
    for game in CLASSICS:
        supports[game] = [part for part in load(NATIVE / game / 'table.json')['blocks']
                          if part['name'].startswith('leg_')]
    return supports


def packed_supports():
    supports = {}
    for game, path in BOARDS.items():
        model = load(PACKED / (path + '.json'))
        supports[game] = (model, [part for part in model['elements'] if part.get('name', '').startswith('table_leg_')])
    for game in ROUNDS:
        model = load(PACKED / game / (game + '_table.json'))
        supports[game] = (model, model['elements'][49:-1])
    model = load(PACKED / 'mahjong/mahjong_table.json')
    supports['mahjong'] = (model, [part for part in model['elements'] if part.get('name', '').startswith('table_leg_')])
    model = load(PACKED / 'yacht/yacht_table.json')
    supports['yacht'] = (model, [part for part in model['elements'] if part.get('name', '').startswith(('dice_leg_', 'score_leg_'))])
    for game in CLASSICS:
        model = load(PACKED / game / (game + '_board.json'))
        supports[game] = (model, [part for part in model['elements'] if part.get('name', '').startswith('leg_')])
    return supports


@lru_cache(maxsize=None)
def opaque_pixels_are_dark(texture):
    path = ROOT / 'resource-pack/assets' / (texture.replace(':', '/textures/') + '.png')
    with Image.open(path) as source:
        image = source.convert('RGBA')
        pixels = image.load()
        opaque = [pixels[x, y] for y in range(image.height) for x in range(image.width) if pixels[x, y][3]]
        return bool(opaque) and all(max(pixel[:3]) < 100 for pixel in opaque)


class TableSupportPaletteTest(unittest.TestCase):
    def test_all_thirty_native_games_use_dark_lower_supports(self):
        games = native_supports()
        self.assertEqual(30, len(games))
        for game, parts in games.items():
            with self.subTest(game=game):
                self.assertTrue(parts)
                self.assertTrue(all(part['material'] == 'BROWN_TERRACOTTA' for part in parts),
                                game + ' lower supports must retain the common dark palette')

    def test_all_thirty_packed_games_use_dark_lower_supports(self):
        games = packed_supports()
        self.assertEqual(30, len(games))
        for game, (model, parts) in games.items():
            with self.subTest(game=game):
                self.assertTrue(parts)
                textures = {model['textures'][face['texture'][1:]] for part in parts for face in part['faces'].values()}
                self.assertTrue(all(opaque_pixels_are_dark(texture) for texture in textures),
                                game + ' support faces must use dark material or dark masked chassis caps')

    def test_shared_ludo_dice_tray_legs_use_the_same_dark_palette(self):
        for size in ['compact', 'large']:
            with self.subTest(size=size):
                parts = load(NATIVE / f'ludo/tray_legs_{size}.json')['blocks']
                self.assertEqual(4, len(parts))
                self.assertTrue(all(part['material'] == 'BROWN_TERRACOTTA' for part in parts))


if __name__ == '__main__':
    unittest.main()
