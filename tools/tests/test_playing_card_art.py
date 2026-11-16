import unittest
import json
from pathlib import Path
from PIL import Image, ImageChops
from tools.package_resource_pack import item_definitions

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'resource-pack/originals/shared/textures/playing-cards'
ITEMS = item_definitions(ROOT / 'resource-pack')


def face(name):
    item = json.loads(ITEMS[name].read_text())
    path = item['model']['model'].replace(':', '/models/') + '.json'
    model = json.loads((ROOT / 'resource-pack/assets' / path).read_text())
    texture = model['elements'][0]['faces']['south']['texture'][1:]
    path = model['textures'][texture].replace(':', '/textures/') + '.png'
    return Image.open(ROOT / 'resource-pack/assets' / path).convert('RGB')


class PlayingCardArtTest(unittest.TestCase):
    def test_playing_card_backs_use_a_flat_blue_field_and_matching_ivory_border(self):
        root = ROOT / 'resource-pack/assets/3dgames/textures/item/shared/playing_cards'
        front = Image.open(root / 'poker_card_back_front.png').convert('RGBA')
        rear = Image.open(root / 'poker_card_back_rear.png').convert('RGBA')
        self.assertIsNone(ImageChops.difference(front, rear).getbbox())
        self.assertEqual({(35, 55, 78, 255)}, set(front.crop((16, 16, 240, 240)).get_flattened_data()))
        self.assertEqual((244, 235, 216, 255), front.getpixel((128, 2)))
        self.assertEqual(184, sum(pixel[3] == 0 for pixel in front.get_flattened_data()))

    def test_all_standard_faces_preserve_the_imported_pixels_and_aspect_ratio(self):
        for kind in ('spades', 'hearts', 'diamonds', 'clubs'):
            for rank in ('ace', *map(str, range(2, 11)), 'jack', 'queen', 'king'):
                with self.subTest(kind=kind, rank=rank):
                    original = Image.open(SOURCE / f'poker_{kind}_{rank}.png').convert('RGB')
                    artwork = face(f'playing_{kind}_{rank}')
                    self.assertEqual((256, 256), artwork.size)
                    self.assertIsNone(ImageChops.difference(
                        original.resize((120, 168), Image.Resampling.NEAREST).resize((256, 256), Image.Resampling.NEAREST),
                        artwork).getbbox())

    def test_small_joker_preserves_the_card_and_recolors_the_design(self):
        original = Image.open(SOURCE / 'poker_joker.png').convert('RGB')
        original = original.resize((120, 168), Image.Resampling.NEAREST).resize((256, 256), Image.Resampling.NEAREST)
        colors = {(255, 68, 99): (51, 51, 51), (204, 55, 79): (20, 20, 20),
                  (255, 195, 56): (230, 230, 230), (204, 153, 44): (170, 170, 170)}
        artwork = face('playing_joker_small')
        self.assertEqual([colors.get(pixel, pixel) for pixel in original.get_flattened_data()],
                         list(artwork.get_flattened_data()))

    def test_jokers_remain_distinguishable(self):
        small, big = face('playing_joker_small'), face('playing_joker_big')
        self.assertIsNotNone(ImageChops.difference(small, big).getbbox())
        self.assertIn((255, 68, 99), set(big.get_flattened_data()))

    def test_mahjong_art_is_larger_centered_and_keeps_a_clear_margin(self):
        for key in ('m1', 'm9', 'p9', 's1', 'z1', 'f1'):
            with self.subTest(key=key):
                artwork = face('mahjong_' + key)
                bounds = artwork.convert('L').point(lambda value: 255 if value < 180 else 0).getbbox()
                self.assertIsNotNone(bounds)
                self.assertGreaterEqual(bounds[0], 8)
                self.assertGreaterEqual(bounds[1], 12)
                self.assertLessEqual(bounds[2], 152)
                self.assertLessEqual(bounds[3], 228)
                self.assertLessEqual(abs(bounds[0] + bounds[2] - 160), 2)
                self.assertLessEqual(abs(bounds[1] + bounds[3] - 240), 2)
                source_name = ITEMS['mahjong_' + key].stem
                original = Image.open(ROOT / f'resource-pack/originals/tabletop/textures/mahjong/tiles/{source_name}.png').convert('RGBA')
                crop = original.getbbox()
                old_scale = min(140 / original.width, 220 / original.height)
                self.assertGreater(bounds[2] - bounds[0], (crop[2] - crop[0]) * old_scale * 1.18)

    def test_white_dragon_stays_blank(self):
        artwork = face('mahjong_z5')
        self.assertEqual((160, 240), artwork.size)
        self.assertGreaterEqual(artwork.convert('L').getextrema()[0], 200,
                                'The ivory tile must stay blank without dark ink')


if __name__ == '__main__':
    unittest.main()
