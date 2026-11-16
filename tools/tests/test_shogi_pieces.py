import hashlib
import json
import unittest
from collections import Counter
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'resource-pack/assets/3dgames'
ORIGINALS = ROOT / 'resource-pack/originals/tabletop/shogi'
COMMIT = '3a7643b8f6aa9910b360b61e062961e70213c6cc'
PIECES = {
    'pawn': ('FU', 'eda66e64813c820e49bb569f179783433c4b495d17480c9a428112ad49cee265'),
    'lance': ('KY', 'b37f0e6b771a8a7593f2579d5154819b63041a767f6adeed23b0b400337e52e9'),
    'knight': ('KE', '1ec6eb68c85d3dc93c98d762c435ee63a2c4e3451ccef8f80a858d0d5b4baf7c'),
    'silver': ('GI', 'c6d4a50fa197002d1b389c54a6cafe774ee51deef10f520530b55eb8d1498d29'),
    'gold': ('KI', '014f3f4b5a8e97101fc3e3b6209e5561bccd0ec2b1984420b02e69051ed09a48'),
    'bishop': ('KA', 'ab61fe2fc88701e971d4e23b32d2f2b2b51e480d945860bd2c00f5279829ca23'),
    'rook': ('HI', 'fe7189ed76a28cc164656de426f0b224a114bc887fbb4ee77b9f5d9b9ca40f79'),
    'king': ('OU', 'c277728d7ec9051baecdae986052b3d26b404c6e8328c5c94968fd65583ec318'),
    'jeweled_king': ('GY', '4b001540d5e026e97b7959a1c05a3e42b39c33839abb8120231c368552db439f'),
    'promoted_pawn': ('TO', '808846f5cce83750c4ce38d45affa09e2f0e5b583f1e599a87ec641548144889'),
    'promoted_lance': ('NY', '3fa37c462190644185423bcaf92f6bb440091dfcafb3090d5c22d834e341f72f'),
    'promoted_knight': ('NK', '7cc12943c813636e097f59126286e1c780956d3352edbf41e02c20cb2c27f9da'),
    'promoted_silver': ('NG', '8caa79c5dc142ec4841477dc35b25bd1a756b6a85bde588db75418b0d1d976dc'),
    'promoted_bishop': ('UM', '310e93294e54a31be42ca1080b9eca63464a090c6849e6cbce1bcc4a26e84f6c'),
    'promoted_rook': ('RY', '420d163473fac64730acd8ac28c3bfea0eb8c985f6168fc31d0280cfe3764331'),
}


def model_path(piece):
    return ASSETS / f'models/item/tabletop/shogi/pieces/shogi_piece_{piece}.json'


def face_vertices(element, face):
    low, high = element['from'], element['to']
    axis = {'west': 0, 'east': 0, 'down': 1, 'up': 1, 'north': 2, 'south': 2}[face]
    other = [i for i in range(3) if i != axis]
    plane = high[axis] if face in ('east', 'up', 'south') else low[axis]
    vertices = []
    for first, second in ((0, 0), (1, 0), (1, 1), (0, 1)):
        point = [0, 0, 0]
        point[axis] = plane
        point[other[0]] = (low, high)[first][other[0]]
        point[other[1]] = (low, high)[second][other[1]]
        vertices.append(tuple(round(value, 7) for value in point))
    return vertices


class ShogiPiecesTest(unittest.TestCase):
    def test_fifteen_items_resolve_to_their_registered_models_and_textures(self):
        registration = ROOT / 'resource-pack/craftengine/resources/3dgames/configuration/tabletop/shogi_pieces.yml'
        self.assertTrue(registration.is_file(), registration)
        entries = {}
        current = None
        for line in registration.read_text(encoding='utf-8').splitlines():
            if line.startswith('  3dgames:'):
                current = line.strip()[:-1]
                entries[current] = {}
            elif line.startswith('    ') and current and ':' in line:
                key, value = line.strip().split(':', 1)
                entries[current][key] = value.strip()
        self.assertEqual(15, len(entries))
        for piece in PIECES:
            with self.subTest(piece=piece):
                stem = 'shogi_piece_' + piece
                key = '3dgames:tabletop/shogi/pieces/' + stem
                item = ASSETS / f'items/tabletop/shogi/pieces/{stem}.json'
                self.assertTrue(item.is_file(), item)
                self.assertEqual({'material': 'paper', 'item_model': key}, entries['3dgames:' + stem])
                self.assertEqual({'model': {'type': 'minecraft:model', 'model': key.replace(':', ':item/', 1)}},
                                 json.loads(item.read_text()))
                model = json.loads(model_path(piece).read_text())
                self.assertEqual({'art': '3dgames:item/tabletop/shogi/pieces/' + stem}, model['textures'])

    def test_original_sources_keep_the_pinned_upstream_bytes(self):
        for piece, (_, expected) in PIECES.items():
            with self.subTest(piece=piece):
                original = ORIGINALS / f'shogi_piece_{piece}.svg'
                self.assertTrue(original.is_file(), original)
                self.assertEqual(expected, hashlib.sha256(original.read_bytes()).hexdigest())
        manifest = ORIGINALS / 'source-manifest.json'
        self.assertTrue(manifest.is_file(), manifest)
        self.assertEqual(COMMIT, json.loads(manifest.read_text())['commit'])

    def test_rasters_keep_dimensions_transparency_and_source_mapping(self):
        manifest_path = ORIGINALS / 'source-manifest.json'
        self.assertTrue(manifest_path.is_file(), manifest_path)
        manifest = {entry['name']: entry for entry in json.loads(manifest_path.read_text())['files']}
        for piece, (code, _) in PIECES.items():
            with self.subTest(piece=piece):
                texture = ASSETS / f'textures/item/tabletop/shogi/pieces/shogi_piece_{piece}.png'
                self.assertTrue(texture.is_file(), texture)
                source = manifest[f'shogi_piece_{piece}.svg']
                self.assertEqual(f'2-kanji_red_wood/0{code}.svg', source['upstream_path'])
                self.assertIn('raster_sha256', source)
                self.assertEqual(hashlib.sha256(texture.read_bytes()).hexdigest(), source['raster_sha256'])
                with Image.open(texture) as image:
                    self.assertEqual((128, 128), image.size)
                    self.assertEqual('RGBA', image.mode)
                    self.assertEqual((0, 255), image.getchannel('A').getextrema())
                    self.assertEqual(0, image.getpixel((0, 0))[3])
                    self.assertEqual(255, image.getpixel((40, 104))[3])

    def test_models_have_a_closed_solid_body_without_duplicate_faces(self):
        for piece in PIECES:
            with self.subTest(piece=piece):
                path = model_path(piece)
                self.assertTrue(path.is_file(), path)
                model = json.loads(path.read_text())
                self.assertEqual([2, 2, 2], model['display']['fixed']['scale'])
                self.assertLessEqual(sum(len(element['faces']) for element in model['elements']), 30)
                art = [element for element in model['elements'] if element['name'] == 'face_art']
                self.assertEqual(1, len(art))
                self.assertEqual({'up'}, set(art[0]['faces']))
                self.assertAlmostEqual(9.68, art[0]['from'][1])
                self.assertAlmostEqual(9.68, art[0]['to'][1])
                faces = [face_vertices(element, direction) for element in model['elements']
                         if element['name'] != 'face_art' for direction in element['faces']]
                self.assertEqual(len(faces), len({tuple(sorted(face)) for face in faces}))
                vertices = set(point for face in faces for point in face)
                edges = Counter()
                for face in faces:
                    for start, end in zip(face, face[1:] + face[:1]):
                        axes = [i for i in range(3) if start[i] != end[i]]
                        self.assertEqual(1, len(axes))
                        axis = axes[0]
                        cuts = sorted(point for point in vertices
                                      if all(point[i] == start[i] for i in range(3) if i != axis)
                                      and min(start[axis], end[axis]) <= point[axis] <= max(start[axis], end[axis]))
                        for first, second in zip(cuts, cuts[1:]):
                            edges[tuple(sorted((first, second)))] += 1
                self.assertTrue(edges)
                self.assertTrue(all(count == 2 for count in edges.values()), edges)
                self.assertEqual(4, min(point[0] for point in vertices))
                self.assertEqual(12, max(point[0] for point in vertices))
                self.assertEqual(4, min(point[2] for point in vertices))
                self.assertEqual(12, max(point[2] for point in vertices))
                self.assertEqual(8, min(point[1] for point in vertices))
                self.assertAlmostEqual(9.6, max(point[1] for point in vertices))

    def test_both_attributions_and_upstream_license_are_retained(self):
        license_path = ROOT / 'resource-pack/licenses/shogi-art.txt'
        self.assertTrue(license_path.is_file(), license_path)
        text = license_path.read_text(encoding='utf-8')
        for term in ('Kadagaden', 'Hari Seldon', 'CC BY 4.0', 'CC BY-SA 3.0', COMMIT):
            self.assertIn(term, text)
        for name in ('upstream-readme.md', 'upstream-license.txt'):
            self.assertTrue((ORIGINALS / name).is_file())

    def test_texture_atlas_remains_below_the_existing_pixel_budget(self):
        total = 0
        for texture in (ASSETS / 'textures').rglob('*.png'):
            with Image.open(texture) as image:
                total += image.width * image.height
        self.assertLess(total, 40_000_000)


if __name__ == '__main__':
    unittest.main()
