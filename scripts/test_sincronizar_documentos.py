import json
import unittest
from unittest.mock import Mock, patch
from sincronizar_documentos import Official, Catalogue, classify, official_url, season_name, export_catalogue

class DocumentsTests(unittest.TestCase):
    def test_season_and_scope(self):
        self.assertEqual(season_name('2026-2027'), '2026/27')
        with self.assertRaises(ValueError):
            season_name('2026/28')
        self.assertEqual(classify('Circular 4 M18 y M16'), ('circular',['M16','M18']))
        self.assertEqual(classify('Variaciones a las Reglas M14'), ('reglamento',['M14']))

    def test_hosts_are_exact_and_https(self):
        for url in ('https://ferugby.es.ejemplo.com/doc.pdf','http://ferugby.es/doc.pdf','https://user@ferugby.es/doc.pdf'):
            with self.assertRaises(ValueError):
                official_url(url)

    def test_future_season_never_uses_current_far(self):
        client=Official()
        client.fetch=Mock(return_value=b'<a class="seasonButton" data-season="15">Temporada 2026 - 2027</a>')
        self.assertEqual(client.far('2027/28'),([],False))
        self.assertEqual(client.fetch.call_count,1)

    def test_far_rejects_server_returning_wrong_season(self):
        client=Official()
        client.fetch=Mock(side_effect=[b'<a class="seasonButton" data-season="16">Temporada 2027 - 2028</a>',b'Temporada 2026 - 2027'])
        with self.assertRaises(ValueError):
            client.far('2027/28')

    def test_fer_dynamic_folder_and_pdf_filter(self):
        client=Official()
        html=b'<a data-idcat="1793" title="Circulares 26-27"></a><input id="wpfd_root_category_id" value="1229">'
        response=json.dumps(dict(files=[dict(ext='pdf',linkdownload='https://ferugby.es/documento.pdf',post_title='Circular M14'),dict(ext='xlsx')],pagination=False)).encode()
        client.fetch=Mock(side_effect=[html,response])
        docs,published=client.fer('2026/27')
        self.assertTrue(published)
        self.assertEqual(len(docs),1)
        self.assertEqual(docs[0]['provenance'],'temporada_en_fuente')

    def test_fer_detects_incomplete_listing(self):
        client=Official()
        client.fetch=Mock(side_effect=[b'<a data-idcat="1793" title="Circulares 26-27"></a><input id="wpfd_root_category_id" value="1229">',b'{"files":[],"pagination":true}'])
        with self.assertRaises(ValueError):
            client.fer('2026/27')

    def test_pdf_declaring_another_season_is_rejected(self):
        client=Official()
        client.fetch=Mock(return_value=b'%PDF-fake')
        reader=Mock(is_encrypted=False,pages=[Mock()])
        reader.pages[0].extract_text.return_value='TEMPORADA 2025/2026'
        with patch('sincronizar_documentos.PdfReader',return_value=reader):
            with self.assertRaises(ValueError):
                client.pdf(dict(url='https://ferugby.es/doc.pdf',provenance='temporada_en_fuente'),'2026/27')

    def test_storage_deduplicates_and_registration_never_applies_rules(self):
        client=object.__new__(Catalogue)
        client.uploaded=set()
        client.request=Mock(side_effect=[b'OK',dict(documento_id=1,version=1,nueva_version=True,reglas_aplicadas=False),dict(documento_id=1,version=1,nueva_version=False,reglas_aplicadas=False)])
        doc=dict(source='FER',url='https://ferugby.es/doc.pdf',title='Circular M14',provenance='temporada_en_fuente')
        client.register(doc,1,b'%PDF','a'*64)
        client.register(doc,1,b'%PDF','a'*64)
        self.assertEqual(client.request.call_count,3)
        self.assertEqual(client.request.call_args.args[1]['p_temporada_id'],1)


    def test_export_uses_current_version_and_keeps_seasons(self):
        import tempfile
        from pathlib import Path
        data={
            'temporadas':[dict(id=1,nombre='2025/26'),dict(id=2,nombre='2026/27')],
            'documentos_oficiales':[dict(id=10,fuente='FAR',url_oficial='https://farugby.com/doc.pdf',titulo='Circular M14',tipo='circular',categorias=['M14'],version_actual=2)],
            'documentos_versiones':[dict(documento_id=10,version=1,archivo='old.pdf'),dict(documento_id=10,version=2,archivo='new.pdf')],
            'documentos_temporadas':[dict(documento_id=10,temporada_id=2,procedencia='temporada_en_fuente',reglas_estado='pendiente_revision')],
            'documentos_sincronizaciones':[dict(temporada_id=2,fuente='FAR',estado='actualizada')]
        }
        client=Mock()
        client.base='https://public.supabase.co'
        client.get.side_effect=lambda table,params:data[table]
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'catalogue.json'
            export_catalogue(client,str(path))
            payload=json.loads(path.read_text())
        self.assertEqual([s['name'] for s in payload['seasons']],['2025/26','2026/27'])
        self.assertEqual(payload['seasons'][0]['documents'],[])
        doc=payload['seasons'][1]['documents'][0]
        self.assertTrue(doc['url'].endswith('/new.pdf'))
        self.assertEqual(doc['version'],2)
        self.assertEqual(doc['review'],'pendiente_revision')
        self.assertNotIn('key',doc)

if __name__=='__main__':
    unittest.main()
