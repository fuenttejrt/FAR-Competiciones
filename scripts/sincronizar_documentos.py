"""Archive official FAR/FER PDFs by season. Never activate competition rules."""
import hashlib
import io
import json
import os
import re
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from bs4 import BeautifulSoup
from pypdf import PdfReader

HOSTS = {'farugby.com', 'www.farugby.com', 'farugby.matchready.es', 'ferugby.es', 'www.ferugby.es'}
LIMIT = 20_000_000

def official_url(url):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != 'https' or parsed.hostname not in HOSTS or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError('Origen no oficial')
    return url

class OfficialRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        official_url(newurl)
        return super().redirect_request(request, fp, code, message, headers, newurl)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def season_name(value):
    match = re.fullmatch(r'(20\d{2})\s*[/–-]\s*(20\d{2}|\d{2})', value.strip())
    if not match:
        raise ValueError('Temporada no reconocida')
    start, end = int(match[1]), int(match[2])
    if (start + 1) % 100 != end % 100:
        raise ValueError('Temporada no consecutiva')
    return f'{start}/{(start+1)%100:02d}'

def norm(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value.casefold()) if not unicodedata.combining(c))

def classify(title):
    text = norm(title)
    categories = sorted(set('M'+m for m in re.findall(r'\b(?:m|sub)[ -]?(6|8|10|12|14|16|18|20|22|23)\b', text)), key=lambda c:int(c[1:]))
    if any(word in text for word in ('senior', 'territorial', 'division de honor', 'dhm', 'dhf', 'dh elite')):
        categories.append('Sénior femenino' if 'femen' in text or 'dhf' in text else 'Sénior')
    if not categories:
        categories = ['Generales']
    kind = 'anexo' if 'anexo' in text else 'reglamento' if any(w in text for w in ('reglamento', 'variaciones', 'reglas')) else 'calendario' if 'calendario' in text else 'circular' if 'circular' in text else 'otro'
    return kind, categories

class Official:
    def __init__(self):
        self.opener = urllib.request.build_opener(OfficialRedirect())
        self.cache = {}

    def fetch(self, url, data=None):
        official_url(url)
        if data is None and url in self.cache:
            return self.cache[url]
        request = urllib.request.Request(url, data=data, headers={'User-Agent':'FAR-Competiciones/127 (documentos oficiales)'})
        with self.opener.open(request, timeout=45) as response:
            official_url(response.url)
            raw = response.read(LIMIT+1)
        if len(raw)>LIMIT:
            raise ValueError('Documento demasiado grande')
        if data is None:
            self.cache[url] = raw
        return raw

    def far(self, season):
        base = 'https://farugby.matchready.es/es/public/documentation/1/index/'
        soup = BeautifulSoup(self.fetch(base), 'html.parser')
        target = None
        for button in soup.select('a.seasonButton[data-season]'):
            try:
                name = season_name(re.sub(r'^Temporada\s*', '', button.get_text(' ',strip=True), flags=re.I))
            except ValueError:
                continue
            if name == season:
                target = button['data-season']
                break
        if target is None:
            return [], False
        raw = self.fetch(base, urllib.parse.urlencode({'season':target}).encode())
        soup = BeautifulSoup(raw, 'html.parser')
        # Verify the server honoured the selected season, not just a menu option.
        text = soup.get_text(' ',strip=True)
        first_season = re.search(r'Temporada\s+(20\d{2}\s*[-/]\s*(?:20)?\d{2})',text)
        if not first_season or season_name(first_season[1]) != season:
            raise ValueError('Indice FAR de otra temporada')
        docs = []
        for row in soup.select('table tr'):
            cells = row.find_all('td')
            if len(cells)<2:
                continue
            for link in row.select('a[href]'):
                url = urllib.parse.urljoin(base,link['href'])
                if urllib.parse.urlparse(url).path.lower().endswith('.pdf'):
                    docs.append(dict(url=official_url(url), title=' · '.join(c.get_text(' ',strip=True) for c in cells[:2]), provenance='temporada_en_fuente'))
        if not docs:
            raise ValueError('Indice FAR vacio')
        return docs, True

    def fer(self, season):
        soup = BeautifulSoup(self.fetch('https://ferugby.es/circulares/'),'html.parser')
        short = season[2:4]+'-'+season[-2:]
        cats = [a['data-idcat'] for a in soup.select('a[data-idcat]') if re.fullmatch(r'Circulares\s+'+re.escape(short),a.get('title','').strip(),re.I)]
        if not cats:
            return [], False
        if len(set(cats))!=1:
            raise ValueError('Carpeta FER ambigua')
        root = soup.select_one('#wpfd_root_category_id')
        if not root:
            raise ValueError('Indice FER desconocido')
        params = dict(juwpfisadmin='false',action='wpfd',task='files.display',view='files',id=cats[0],rootcat=root['value'],page=1,page_limit=200,orderCol='ordering',orderDir='asc')
        data = json.loads(self.fetch('https://ferugby.es/wp-admin/admin-ajax.php?'+urllib.parse.urlencode(params)))
        if not isinstance(data.get('files'), list) or data.get('pagination'):
            raise ValueError('Indice FER incompleto o paginado')
        docs=[]
        for item in data['files']:
            if item.get('ext','').lower()=='pdf':
                docs.append(dict(url=official_url(item['linkdownload']),title=item['post_title'],provenance='temporada_en_fuente'))
        if not docs:
            raise ValueError('Carpeta FER vacia')
        return docs, True

    def regulations(self, source):
        pages = ['https://farugby.matchready.es/es/public/documentation/3/index/'] if source=='FAR' else ['https://ferugby.es/legislacion-fer/','https://ferugby.es/reglamentos-de-juego/']
        docs={}
        for page in pages:
            soup=BeautifulSoup(self.fetch(page),'html.parser')
            for link in soup.select('a[href]'):
                url=urllib.parse.urljoin(page,link['href'])
                if not urllib.parse.urlparse(url).path.lower().endswith('.pdf'):
                    continue
                row=link.find_parent('tr')
                cells=row.find_all('td') if row else []
                title=' · '.join(c.get_text(' ',strip=True) for c in cells[:2]) if cells else link.get('title') or link.get_text(' ',strip=True)
                if not title or title.lower() in ('descargar','vista previa'):
                    title=urllib.parse.unquote(urllib.parse.urlparse(url).path.rsplit('/',1)[-1]).replace('-',' ')
                docs[url]=dict(url=official_url(url),title=title[:1000],provenance='vigencia_por_revisar')
        if not docs and source=='FAR' and 'Todavía no se ha añadido documentación' in soup.get_text(' ',strip=True):
            return []
        if not docs:
            raise ValueError('Indice de reglamentos vacio')
        return list(docs.values())

    def pdf(self, doc, season):
        raw=self.fetch(doc['url'])
        if not raw.startswith(b'%PDF-'):
            raise ValueError('El enlace no devuelve PDF')
        reader=PdfReader(io.BytesIO(raw))
        if reader.is_encrypted or not 1<=len(reader.pages)<=500:
            raise ValueError('PDF no legible')
        if doc['provenance']=='temporada_en_fuente':
            heading=' '.join((p.extract_text() or '') for p in reader.pages[:2])[:5000]
            declared=re.search(r'TEMPORADA\s*[:.-]?\s*(20\d{2})\s*[/–-]\s*((?:20)?\d{2})',heading,re.I)
            if declared and season_name(declared[1]+'/'+declared[2])!=season:
                raise ValueError('PDF declara otra temporada')
        return raw, hashlib.sha256(raw).hexdigest()

class Catalogue:
    def __init__(self):
        self.base=os.environ['SUPABASE_URL'].rstrip('/')
        parsed=urllib.parse.urlparse(self.base)
        if parsed.scheme!='https' or not parsed.hostname or not parsed.hostname.endswith('.supabase.co') or parsed.path or parsed.username or parsed.password or parsed.query:
            raise ValueError('Configuracion no valida')
        self.key=os.environ['SUPABASE_SECRET_KEY']
        self.opener=urllib.request.build_opener(NoRedirect())
        self.uploaded=set()

    def request(self,path,data=None,method='GET',binary=False):
        headers={'apikey':self.key}
        if binary:
            headers.update({'Content-Type':'application/pdf','x-upsert':'false'})
            body=data
        else:
            headers['Content-Type']='application/json'
            body=None if data is None else json.dumps(data).encode()
        req=urllib.request.Request(self.base+path,data=body,headers=headers,method=method)
        with self.opener.open(req,timeout=45) as response:
            raw=response.read(LIMIT+1)
        if len(raw)>LIMIT:
            raise ValueError('Respuesta excesiva')
        return raw if binary or not raw else json.loads(raw)

    def get(self,table,params):
        rows=[]
        while True:
            query=dict(params,limit=200,offset=len(rows))
            page=self.request('/rest/v1/'+table+'?'+urllib.parse.urlencode(query))
            rows.extend(page)
            if len(page)<200:
                return rows

    def state(self,season_id,source,state,detail):
        query=urllib.parse.urlencode(dict(temporada_id='eq.'+str(season_id),fuente='eq.'+source))
        self.request('/rest/v1/documentos_sincronizaciones?'+query,dict(estado=state,revisada_en=datetime.now(timezone.utc).isoformat(),detalle=detail),method='PATCH')

    def upload(self,raw,digest):
        if digest in self.uploaded:
            return
        try:
            self.request('/storage/v1/object/documentos-oficiales/'+digest+'.pdf',raw,'POST',True)
        except urllib.error.HTTPError as error:
            try:
                detail=json.loads(error.read())
            except Exception:
                raise ValueError('Error de almacenamiento') from None
            if error.code not in (400,409) or str(detail.get('error','')).lower() not in ('duplicate','resourcealreadyexists'):
                raise ValueError('Error de almacenamiento') from None
            # A duplicate must contain exactly the expected bytes. Never overwrite.
            url=self.base+'/storage/v1/object/public/documentos-oficiales/'+digest+'.pdf'
            with self.opener.open(url,timeout=45) as response:
                stored=response.read(LIMIT+1)
            if hashlib.sha256(stored).hexdigest()!=digest:
                raise ValueError('Archivo existente discrepante')
        self.uploaded.add(digest)

    def register(self,doc,season_id,raw,digest):
        kind,categories=classify(doc['title'])
        self.upload(raw,digest)
        result=self.request('/rest/v1/rpc/registrar_documento_oficial',dict(p_temporada_id=season_id,p_fuente=doc['source'],p_url=doc['url'],p_titulo=doc['title'][:1000],p_tipo=kind,p_categorias=categories,p_sha256=digest,p_tamano=len(raw),p_procedencia=doc['provenance']),'POST')
        if result.get('reglas_aplicadas') is not False or type(result.get('version')) is not int or result['version']<1:
            raise ValueError('Registro no confirmado')
        return result

def main():
    catalogue=Catalogue()
    official=Official()
    seasons=catalogue.get('temporadas',{'select':'id,nombre','order':'id.asc'})
    jobs=catalogue.get('documentos_sincronizaciones',{'select':'temporada_id,fuente','order':'temporada_id.asc,fuente.asc'})
    enabled={s['codigo'] for s in catalogue.get('documentos_fuentes',{'select':'codigo,habilitada','order':'codigo.asc'}) if s['habilitada']}
    names={s['id']:season_name(s['nombre']) for s in seasons}
    errors=0
    for job in jobs:
        season_id,source=job['temporada_id'],job['fuente']
        if source not in enabled:
            continue
        counts=Counter()
        try:
            if source not in ('FAR','FER') or season_id not in names:
                raise ValueError('Solicitud desconocida')
            season=names[season_id]
            catalogue.state(season_id,source,'en_curso','Buscando documentos oficiales')
            docs,published=(official.far(season) if source=='FAR' else official.fer(season))
            # General regulations are archived with unconfirmed applicability, never inherited as rules.
            docs.extend(official.regulations(source))
            unique={d['url']:d for d in docs}
            for doc in unique.values():
                doc['source']=source
                try:
                    raw,digest=official.pdf(doc,season)
                    result=catalogue.register(doc,season_id,raw,digest)
                    counts['documentos']+=1
                    counts['versiones_nuevas' if result['nueva_version'] else 'sin_cambios']+=1
                except Exception as error:
                    counts['errores']+=1
                    print('DOCUMENTO PENDIENTE 127',source,season,doc['url'],type(error).__name__,str(error)[:120],flush=True)
            state='error' if counts['errores'] else 'actualizada' if published else 'pendiente_publicacion'
            catalogue.state(season_id,source,state,json.dumps(dict(counts),sort_keys=True))
            errors+=counts['errores']
            print('DOCUMENTOS 127',source,season,state,json.dumps(dict(counts),sort_keys=True),flush=True)
        except Exception as error:
            errors+=1
            catalogue.state(season_id,source,'error','No se pudo completar la consulta oficial')
            print('FUENTE PENDIENTE 127',source,type(error).__name__,flush=True)
    if not jobs:
        raise SystemExit('Catalogo sin solicitudes de temporada')
    if errors:
        raise SystemExit('Hay documentos pendientes de revision o descarga')
    print('CATALOGO 127 sincronizado; reglas y categorias deportivas sin cambios',flush=True)

if __name__=='__main__':
    main()
