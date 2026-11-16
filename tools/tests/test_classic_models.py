import json
import math
import unittest
from pathlib import Path
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
KINDS = ('2048', 'mancala', 'nine_mens_morris', 'dots_and_boxes', 'mastermind',
         'hex', 'memory', 'backgammon', 'battleship', 'klondike', 'shogi', 'mini_shogi', 'monopoly')


class ClassicModelsTest(unittest.TestCase):
    def test_native_furniture_has_no_separate_corner_accents(self):
        root = ROOT / 'src/main/resources/vanilla-models/tabletop'
        for path in root.rglob('*.json'):
            blocks = json.loads(path.read_text(encoding='utf-8')).get('blocks', [])
            self.assertFalse(any(b['name'].startswith(('corner_', 'future_light_')) for b in blocks),
                             str(path.relative_to(root)))

    def test_2048_native_empty_sockets_use_flat_light_material(self):
        sockets = [block for block in self.native('2048') if block['name'].startswith('socket_')]
        self.assertEqual(16, len(sockets))
        self.assertTrue(all(block['material'] == 'WHITE_CONCRETE' for block in sockets))

    def test_hex_table_inlay_matches_its_wood_base(self):
        blocks = {block['name']: block for block in self.native('hex')}
        self.assertEqual(blocks['wood_base']['material'], blocks['inlay_0']['material'])

    @staticmethod
    def footprint(block):
        angle = math.radians(block.get('rotation', [0, 0, 0])[1])
        c, s = math.cos(angle), math.sin(angle)
        x, _, z = block['position']
        width, _, depth = block['size']
        return [(x + dx * c + dz * s, z - dx * s + dz * c)
                for dx, dz in ((-width/2, -depth/2), (width/2, -depth/2),
                               (width/2, depth/2), (-width/2, depth/2))]

    @staticmethod
    def overlaps(a, b):
        for polygon in (a, b):
            for first, second in zip(polygon, polygon[1:] + polygon[:1]):
                dx, dz = second[0] - first[0], second[1] - first[1]
                length = math.hypot(dx, dz)
                axis = (-dz / length, dx / length)
                left = [p[0] * axis[0] + p[1] * axis[1] for p in a]
                right = [p[0] * axis[0] + p[1] * axis[1] for p in b]
                if min(max(left), max(right)) - max(min(left), min(right)) <= 1e-7:
                    return False
        return True

    def native(self, kind):
        path = ROOT / 'src/main/resources/vanilla-models/tabletop' / kind / 'table.json'
        return json.loads(path.read_text(encoding='utf-8'))['blocks']

    def test_packed_and_native_chess_boxes_use_the_same_complete_geometry(self):
        index = json.loads((ROOT / 'src/main/resources/vanilla-models/index.json').read_text())
        for kind in KINDS:
            with self.subTest(kind=kind):
                self.assertIn('tabletop/' + kind + '/table', index)
                native = self.native(kind)
                path = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop' / kind / (kind + '_board.json')
                packed = json.loads(path.read_text())
                if kind == 'hex':
                    self.assertEqual(1, len([p for p in packed['elements'] if p['name'] == 'hex_board_face']))
                    self.assertTrue(all(b['name'].startswith(('diamond_fill_', 'hex_grid_', 'boundary_'))
                                        for b in native if b['name'] not in {p['name'] for p in packed['elements']}))
                    native = [b for b in native if b['name'] in {p['name'] for p in packed['elements']}]
                else:
                    self.assertEqual(len(native), len(packed['elements']))
                self.assertLessEqual(len(native), 500)
                self.assertLessEqual(sum(len(p['faces']) for p in packed['elements']), 2200)
                self.assertEqual([2, 2, 2], packed['display']['fixed']['scale'])
                for block, part in zip(native, packed['elements']):
                    self.assertEqual(block['name'], part['name'])
                    rotation = block.get('rotation', [0, 0, 0])
                    if any(rotation):
                        self.assertEqual('y', part['rotation']['axis'])
                        self.assertEqual(rotation[1], part['rotation']['angle'])
                        for axis in range(3):
                            self.assertAlmostEqual(block['position'][axis] * 8 + 8,
                                                   part['rotation']['origin'][axis], places=5)
                    for axis in range(3):
                        low = block['position'][axis] - block['size'][axis] / 2
                        high = block['position'][axis] + block['size'][axis] / 2
                        self.assertAlmostEqual(low, (part['from'][axis] - 8) / 8, places=5)
                        self.assertAlmostEqual(high, (part['to'][axis] - 8) / 8, places=5)
                    self.assertTrue(all(-16 <= v <= 32 for v in part['from'] + part['to']))

    def test_static_box_top_faces_never_overlap_at_the_same_height(self):
        for kind in KINDS:
            with self.subTest(kind=kind):
                blocks = self.native(kind)
                for i, a in enumerate(blocks):
                    for b in blocks[i + 1:]:
                        if abs(a['position'][1] + a['size'][1] / 2 - b['position'][1] - b['size'][1] / 2) > 1e-7:
                            continue
                        self.assertFalse(self.overlaps(self.footprint(a), self.footprint(b)),
                                         f'{kind}: coplanar {a["name"]} / {b["name"]}')

    def test_mancala_has_fourteen_open_wells_and_a_floor_below_the_lip(self):
        blocks = self.native('mancala')
        floors = [block for block in blocks if block['name'].startswith('well_floor_')]
        self.assertEqual(14, len(floors))
        for floor in floors:
            x, _, z = floor['position']
            self.assertLess(floor['position'][1] + floor['size'][1] / 2, -.025)
            for block in blocks:
                low = [block['position'][k] - block['size'][k] / 2 for k in range(3)]
                high = [block['position'][k] + block['size'][k] / 2 for k in range(3)]
                self.assertFalse(low[0] < x < high[0] and low[2] < z < high[2] and low[1] < -.02 < high[1],
                                 'A solid panel must not plug the seed well')

    def test_mastermind_has_ten_horizontal_columns_and_four_secret_wells(self):
        blocks = {block['name']: block for block in self.native('mastermind')}
        for attempt in range(10):
            for slot in range(4):
                floor = blocks[f'well_floor_g{attempt}_{slot}']
                self.assertAlmostEqual(-.85 + attempt * .165, floor['position'][0])
                self.assertAlmostEqual((slot - 1.5) * .20 + .08, floor['position'][2])
                self.assertAlmostEqual(.0115, floor['position'][1] + floor['size'][1] / 2)
            for hint in range(4):
                floor = blocks[f'feedback_floor_{attempt}_{hint}']
                self.assertAlmostEqual(-.85 + attempt * .165 + (hint % 2 - .5) * .055,
                                       floor['position'][0])
                self.assertAlmostEqual(-.50 + (hint // 2 - .5) * .055, floor['position'][2])
        self.assertFalse(any(name.startswith('well_floor_draft') for name in blocks))
        for slot in range(4):
            self.assertAlmostEqual(.94, blocks[f'well_floor_secret{slot}']['position'][0])
        self.assertTrue(all(block['material'] in {'BLACK_CONCRETE', 'GRAY_CONCRETE',
                                                'WHITE_CONCRETE', 'BROWN_CONCRETE', 'BROWN_TERRACOTTA', 'YELLOW_TERRACOTTA'}
                            for block in blocks.values()))
        for color in range(6):
            tray = blocks[f'palette_floor_{color}']
            self.assertAlmostEqual((color - 2.5) * .27, tray['position'][0])
            self.assertAlmostEqual(.77, tray['position'][2])

    def test_mastermind_six_closed_stepped_peg_models_match_native_cap_dimensions(self):
        config = (ROOT / 'resource-pack/craftengine/resources/3dgames/configuration/tabletop/classic.yml').read_text()
        for color, name in enumerate(('blue', 'red', 'green', 'yellow', 'pink', 'white')):
            with self.subTest(color=name):
                model_name = 'mastermind_peg_' + name
                item = ROOT / 'resource-pack/assets/3dgames/items/tabletop/mastermind' / (model_name + '.json')
                self.assertTrue(item.is_file(), model_name)
                self.assertEqual('3dgames:item/tabletop/mastermind/' + model_name,
                                 json.loads(item.read_text())['model']['model'])
                path = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop/mastermind' / (model_name + '.json')
                model = json.loads(path.read_text())
                self.assertEqual(6, len(model['elements']))
                expected = [('stem', [0, .0045, 0], [.032, .010, .032])]
                for index, (width, top) in enumerate(zip((.06, .09, .104, .09, .06),
                                                       (.026, .031, .034, .031, .026))):
                    expected.append((f'cap_{index}', [0, (.0095 + top) / 2, (index - 2) * .02],
                                     [width, top - .0095, .02]))
                for part, (part_name, center, size) in zip(model['elements'], expected):
                    self.assertEqual(part_name, part['name'])
                    self.assertEqual({'up', 'down', 'north', 'south', 'east', 'west'}, set(part['faces']))
                    for axis in range(3):
                        self.assertAlmostEqual(center[axis] - size[axis] / 2, (part['from'][axis] - 8) / 8)
                        self.assertAlmostEqual(center[axis] + size[axis] / 2, (part['to'][axis] - 8) / 8)
                self.assertEqual([2, 2, 2], model['display']['fixed']['scale'])
                self.assertIn(f'3dgames:classic_mastermind_peg_{color}:', config)
                self.assertIn('item_model: 3dgames:tabletop/mastermind/' + model_name, config)
                for texture in model['textures'].values():
                    path = ROOT / 'resource-pack/assets' / (texture.replace(':', '/textures/') + '.png')
                    pixels = Image.open(path).convert('RGBA')
                    self.assertEqual(1, len(set(pixels.get_flattened_data())))

    def test_connect_four_frame_stays_blue_in_both_renderers(self):
        path = ROOT / 'src/main/resources/vanilla-models/tabletop/connect_four/rack.json'
        rack = json.loads(path.read_text())['blocks']
        frame = [block for block in rack if block['material'] != 'WAXED_EXPOSED_COPPER']
        self.assertTrue(frame)
        self.assertTrue(all(block['material'] == 'BLUE_CONCRETE' for block in frame),
                        'The frame must contrast with red and yellow playing discs')
        path = ROOT / 'resource-pack/assets/3dgames/models/item/tabletop/connect_four/connect_four_board.json'
        model = json.loads(path.read_text())
        self.assertIn('blue', model['textures']['BLUE_CONCRETE'])
        for name in ('connect_four_board_faces', 'connect_four_support_end_caps'):
            path = ROOT / 'resource-pack/assets/3dgames/textures/item/tabletop/connect_four' / (name + '.png')
            pixels = list(Image.open(path).convert('RGBA').get_flattened_data())
            opaque = [(r, g, b) for r, g, b, a in pixels if a == 255]
            self.assertTrue(opaque)
            self.assertTrue(all(b > r + 45 and b > g + 35 for r, g, b in opaque),
                            name + ' must retain a clear blue frame')

    def test_hex_corners_point_at_four_table_sides_and_all_tiles_fit_inside(self):
        blocks = self.native('hex')
        base = next(block for block in blocks if block['name'] == 'wood_base')
        self.assertGreater(base['size'][0], base['size'][2] * 1.25)
        edges = [block for block in blocks if block['name'].startswith('hex_grid_')]
        self.assertEqual(406, len(edges))
        self.assertEqual(4, len([b for b in blocks if b['name'].startswith('boundary_')]))
        for block in edges:
            for x, z in self.footprint(block):
                self.assertLess(abs(x), base['size'][0] / 2)
                self.assertLess(abs(z), base['size'][2] / 2)



if __name__ == '__main__':
    unittest.main()
