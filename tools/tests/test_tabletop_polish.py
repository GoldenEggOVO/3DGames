import json
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


class TabletopPolishTest(unittest.TestCase):
    def test_standard_grids_use_fine_lines_at_the_existing_intersections(self):
        for kind, names in [('gomoku', ['gomoku_board_top']),
                            ('go', ['go_9x9_board_top', 'go_13x13_board_top', 'go_19x19_board_top'])]:
            for name in names:
                image = Image.open(ROOT / f'resource-pack/assets/3dgames/textures/item/tabletop/{kind}/{name}.png').convert('RGB')
                widths, start = [], None
                for x in range(36, 477):
                    dark = max(image.getpixel((x, 212))) < 100
                    if dark and start is None:
                        start = x
                    elif not dark and start is not None:
                        widths.append(x - start)
                        start = None
                self.assertGreaterEqual(len(widths), 9, name)
                self.assertLessEqual(max(widths), 3, name)

    def test_monopoly_has_a_detailed_atlas_and_readable_deep_properties(self):
        image = Image.open(ROOT / 'resource-pack/assets/3dgames/textures/item/tabletop/monopoly/monopoly_board_top.png')
        self.addCleanup(image.close)
        self.assertEqual((1024, 1024), image.size)
        model = json.loads((ROOT / 'src/main/resources/vanilla-models/tabletop/monopoly/table.json').read_text())
        plots = {b['name']: b for b in model['blocks'] if b['name'].startswith('property_')}
        self.assertEqual(40, len(plots))
        self.assertGreater(plots['property_p0']['size'][0], .45)
        self.assertGreater(plots['property_p1']['size'][2], plots['property_p1']['size'][0] * 1.5)

    def test_dots_and_boxes_has_no_duplicate_static_edge_blocks(self):
        for prefix in ['src/main/resources/vanilla-models/tabletop/dots_and_boxes/table.json',
                       'resource-pack/assets/3dgames/models/item/tabletop/dots_and_boxes/dots_and_boxes_board.json']:
            model = json.loads((ROOT / prefix).read_text())
            self.assertFalse(any(b['name'].startswith('socket_') for b in model.get('blocks', model.get('elements', []))))

    def test_hex_has_four_continuous_boundaries_and_a_single_packed_board_face(self):
        native = json.loads((ROOT / 'src/main/resources/vanilla-models/tabletop/hex/table.json').read_text())
        self.assertEqual(4, len([b for b in native['blocks'] if b['name'].startswith('boundary_')]))
        packed = json.loads((ROOT / 'resource-pack/assets/3dgames/models/item/tabletop/hex/hex_board.json').read_text())
        self.assertLess(len(packed['elements']), 30)
        self.assertEqual(1, len([b for b in packed['elements'] if b['name'] == 'hex_board_face']))


if __name__ == '__main__':
    unittest.main()
