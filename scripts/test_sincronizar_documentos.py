import json
import unittest
from unittest.mock import Mock, patch
from sincronizar_documentos import Official, Catalogue, classify, official_url, season_name

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

if __name__=='__main__':
    unittest.main()
