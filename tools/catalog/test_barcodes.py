import unittest

from build_barcodes import normalize_gtin, product_record, build


class BarcodeImportTest(unittest.TestCase):
    def test_canonical_gtin_and_invalid_input(self):
        self.assertEqual('03017620422003', normalize_gtin('3017620422003'))
        self.assertEqual('00012345678905', normalize_gtin('012345678905'))
        for code in ['4006381333932', '0000000000000', '123', '４００６３８１３３３９３１']:
            self.assertIsNone(normalize_gtin(code))

    def product(self, **fields):
        return {'code': '3017620422003', 'product_name': 'Test spread', 'brands': 'Test', **fields}

    def test_unknown_values_stay_unknown_and_identity_is_kept(self):
        p = product_record(self.product())
        self.assertEqual('03017620422003', p['code'])
        self.assertEqual('Test spread', p['name'])
        self.assertIsNone(p['kcal'])
        self.assertIsNone(product_record(self.product(code='4006381333932')))

    def test_explicit_as_sold_packaging_values_and_kj_conversion(self):
        p = product_record(self.product(nutrition={'aggregated_set': {
            'preparation': 'as_sold', 'per': '100g', 'nutrients': {
                'energy-kj': {'value': 836.8, 'unit': 'kJ', 'source': 'packaging'},
                'proteins': {'value': 5, 'unit': 'g', 'source': 'manufacturer'},
                'fat': {'value': 99, 'unit': 'g', 'source': 'estimate'},
            }}}))
        self.assertAlmostEqual(200, p['kcal'])
        self.assertEqual(5, p['protein'])
        self.assertIsNone(p['fat'])
        self.assertIsNone(p['carbs'])

    def test_volume_basis_and_incompatible_pack_never_become_mass(self):
        p = product_record(self.product(product_quantity=500, product_quantity_unit='g', nutrition={'aggregated_set': {
            'preparation': 'as_sold', 'per': '100ml', 'nutrients': {
                'energy-kcal': {'value': 60, 'unit': 'kcal', 'source': 'packaging'}}}}))
        self.assertEqual('VOLUME', p['basis'])
        self.assertEqual(60, p['kcal'])
        self.assertIsNone(p['package_amount'])

    def test_prepared_servings_invalid_units_and_numbers_are_not_imported(self):
        for per, prep in [('serving', 'as_sold'), ('100g', 'prepared')]:
            p = product_record(self.product(nutrition={'aggregated_set': {
                'preparation': prep, 'per': per, 'nutrients': {
                    'energy-kcal': {'value': 200, 'unit': 'kcal', 'source': 'packaging'}}}}))
            self.assertIsNone(p['kcal'])
        p = product_record(self.product(nutrition_data_per='100g', nutriments={
            'energy-kcal_100g': -1, 'proteins_100g': float('nan'), 'fat_100g': 101,
            'carbohydrates_100g': 50, 'carbohydrates_unit': 'mg'}))
        for key in ['kcal', 'protein', 'fat', 'carbs']:
            self.assertIsNone(p[key])

    def test_legacy_explicit_100g_is_preserved_without_prepared_values(self):
        p = product_record(self.product(nutrition_data_per='100g', product_quantity=0.4,
            product_quantity_unit='kg', nutriments={'energy-kcal_100g': 539,
            'proteins_100g': 6.3, 'energy-kcal_prepared_100g': 999}))
        self.assertEqual(539, p['kcal'])
        self.assertEqual(6.3, p['protein'])
        self.assertEqual(400, p['package_amount'])

    def test_actual_parquet_list_nutrients_and_multilingual_name(self):
        p = product_record(self.product(product_name=[{'lang': 'en', 'text': 'Milk'}, {'lang': 'ru', 'text': 'Молоко'}],
            nutrition_data_per='100g', nutriments=[{'name': 'energy-kcal', '100g': 60, 'unit': 'kcal', 'prepared_100g': 999},
            {'name': 'proteins', '100g': 3.2, 'unit': 'g'}]))
        self.assertEqual('Молоко', p['name'])
        self.assertEqual(60, p['kcal'])
        self.assertEqual(3.2, p['protein'])

    def test_dense_volume_can_have_more_than_100g_macros_per_100ml(self):
        p = product_record(self.product(nutrition_data_per='100ml', nutriments={
            'energy-kcal_100g': 448, 'carbohydrates_100g': 112}))
        self.assertEqual('VOLUME', p['basis'])
        self.assertEqual(112, p['carbs'])

    def test_full_pipeline_deduplicates_aliases_and_publishes_portable_database(self):
        try:
            import pyarrow as pa
            import pyarrow.parquet as pq
        except ImportError:
            self.skipTest('Install pyarrow to run the full Parquet pipeline fixture')
        import contextlib, gzip, hashlib, io, json, sqlite3, tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'fixture.parquet'
            rows = [self.product(nutrition_data_per='100g', nutriments=[{'name': 'energy-kcal', '100g': 539, 'unit': 'kcal'}]),
                self.product(code='03017620422003', product_name='Weaker alias'), self.product(code='4006381333932')]
            pq.write_table(pa.Table.from_pylist(rows), source)
            sha = hashlib.sha256(source.read_bytes()).hexdigest()
            with contextlib.redirect_stdout(io.StringIO()):
                build(source, root / 'assets', 'fixture-revision', '2026-10-29', sha)
            report = json.loads((root / 'assets/report.json').read_text())
            self.assertEqual(3, report['source_rows'])
            self.assertEqual(1, report['products'])
            self.assertEqual(1, report['duplicate_alias_rows'])
            self.assertEqual(1, report['invalid_gtin_rows'])
            raw = gzip.decompress((root / 'assets/products.sqlite.gz.part000').read_bytes())
            self.assertEqual(report['database_sha256'], hashlib.sha256(raw).hexdigest())
            restored = root / 'restored.sqlite'
            restored.write_bytes(raw)
            with contextlib.closing(sqlite3.connect(restored)) as db:
                self.assertEqual(('Test spread', 539), db.execute('SELECT name,kcal FROM products').fetchone())
                self.assertEqual(('ODbL-1.0',), db.execute("SELECT value FROM metadata WHERE key='license'").fetchone())


if __name__ == '__main__':
    unittest.main()
