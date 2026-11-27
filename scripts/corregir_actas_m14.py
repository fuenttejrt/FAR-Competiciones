"""Detect and apply validated corrections to existing M14 actas."""
import contextlib
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
from pypdf import PdfReader
from scripts.extract_m14_core import extract_roster_scoring, extract_events, extract_staff

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def norm(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', str(value).casefold()) if c.isalnum())

def build_manifest(raw, content, match, teams, players, official_id, digest):
    for side in ('local', 'visitante'):
        if norm(teams[match['equipo_'+side+'_id']]) not in norm(content):
            raise ValueError('Equipo del PDF discrepante')
    weights = {'E':5, 'T':2, 'D':3, 'PC':3, 'EC':7}
    # First pass discovers totals; the second reconciles the complete scoring columns.
    with contextlib.redirect_stdout(io.StringIO()):
        first = extract_roster_scoring(raw, content, dict(id=match['id'], puntos_local=0, puntos_visitante=0))
        score = [sum(weights[k] for k, _, _ in first['scores'][side]) for side in ('local','visitante')]
        core = extract_roster_scoring(raw, content, dict(id=match['id'], puntos_local=score[0], puntos_visitante=score[1]))
        if not all(core['score_ok'].get(side, False) for side in ('local','visitante')):
            raise ValueError('Marcador no conciliado')
        if not re.search(r'(?<!\d)'+str(score[0])+r'\s*[-:–]\s*'+str(score[1])+r'(?!\d)', content):
            raise ValueError('Marcador textual discrepante')
        events = extract_events(raw, dict(id=match['id']), core)
        if not all(ok for _, ok in events['participation'].values()):
            raise ValueError('Participacion incoherente')
        staff = extract_staff(raw, match, core)
    if len({(s['equipo_id'],s['funcion']) for s in staff}) != len(staff):
        raise ValueError('Personal duplicado')
    pack = dict(partido=match['id'],acta=official_id,url=match['url_acta'],hash_contenido=digest,marcador=score,
                alineaciones=[],anotaciones=[],sustituciones=[],sanciones=[],participaciones=[],personal=staff)
    kinds = {'E':'ensayo','T':'transformacion','D':'drop','PC':'golpe_castigo','EC':'ensayo_castigo'}
    for side in ('local','visitante'):
        team = match['equipo_'+side+'_id']
        ids = core['identities'][side]
        if set(ids) != set(core['roster'][side]) or len(set(ids.values())) != len(ids):
            raise ValueError('Identidades duplicadas')
        for dorsal, licence in ids.items():
            licence = str(licence)
            name = core['names_by_side'][side].get(dorsal,'')
            if not re.fullmatch(r'\d{7}',licence) or len(name.split())<2 or any(c.isdigit() for c in name):
                raise ValueError('Identidad incompleta')
            existing = players.get(licence)
            if existing and norm(existing['nombre_completo'] or '') != norm(name):
                raise ValueError('Identidad requiere revision')
            pack['alineaciones'].append(dict(equipo_id=team,dorsal=dorsal,licencia=licence,nombre_completo=name,jugador_id=existing['id'] if existing else None,titular=dorsal in core['starting'][side]))
            pack['participaciones'].append(dict(equipo_id=team,dorsal=dorsal,minutos_jugados=events['participation'][side][0][dorsal],minutos_sancionado=events['sanction_minutes'][side][dorsal]))
        for kind, minute, dorsal in core['scores'][side]:
            if kind!='EC' and dorsal not in ids:
                raise ValueError('Anotador sin alineacion')
            pack['anotaciones'].append(dict(equipo_id=team,tipo=kinds[kind],minuto=minute,dorsal=dorsal,puntos=weights[kind],orden_evento=len(pack['anotaciones'])+1))
        for minute, kind, dorsals, _ in events['timeline'][side]:
            if any(d not in ids for d in dorsals):
                raise ValueError('Evento sin alineacion')
            if kind=='Cambios':
                if dorsals[0]==dorsals[1]:
                    raise ValueError('Cambio invalido')
                pack['sustituciones'].append(dict(equipo_id=team,minuto=minute,dorsal_entra=dorsals[0],dorsal_sale=dorsals[1],orden_evento=len(pack['sustituciones'])+1))
            else:
                pack['sanciones'].append(dict(equipo_id=team,minuto=minute,dorsal=dorsals[0],tipo={'Expulsiones temporales':'temporal','Expulsiones definitivas':'definitiva'}[kind],orden_evento=len(pack['sanciones'])+1))
    return pack

def main():
    base=os.environ['SUPABASE_URL'].rstrip('/')
    key=os.environ['SUPABASE_SECRET_KEY']
    config=urllib.parse.urlparse(base)
    if config.scheme!='https' or not config.hostname or not config.hostname.endswith('.supabase.co') or config.path or config.query or config.username or config.password:
        raise SystemExit('Configuracion no valida')
    apply=os.environ.get('APLICAR_CORRECCIONES')=='true'
    opener=urllib.request.build_opener(NoRedirect())
    def get(table,params):
        rows=[]
        while True:
            query=dict(params,limit=200,offset=len(rows),order='id.asc')
            request=urllib.request.Request(base+'/rest/v1/'+table+'?'+urllib.parse.urlencode(query),headers={'apikey':key})
            with opener.open(request,timeout=30) as response:
                page=json.load(response)
            rows.extend(page)
            if len(page)<200:
                return rows
    def rpc(name,args):
        request=urllib.request.Request(base+'/rest/v1/rpc/'+name,data=json.dumps(args).encode(),headers={'apikey':key,'Content-Type':'application/json'},method='POST')
        with opener.open(request,timeout=45) as response:
            return json.load(response)
    competitions=get('competiciones',{'id_oficial':'in.(35/11,35/12,35/15)','select':'id'})
    if len(competitions)!=3:
        raise SystemExit('Alcance de competiciones incompleto')
    ids=','.join(str(c['id']) for c in competitions)
    rounds=get('jornadas',{'competicion_id':'in.('+ids+')','select':'id'})
    if not rounds:
        raise SystemExit('Sin jornadas')
    matches=get('partidos',{'jornada_id':'in.('+','.join(str(j['id']) for j in rounds)+')','select':'id,url_acta,equipo_local_id,equipo_visitante_id'})
    teams={t['id']:t['nombre'] for t in get('equipos',{'select':'id,nombre'})}
    players={}
    counts=Counter()
    for match in matches:
        if not match['url_acta']:
            counts['sin_url']+=1
            continue
        try:
            url=urllib.parse.urlparse(match['url_acta'])
            route=re.fullmatch(r'/es/public/competition/match_pdf/(\d+)/act/',url.path)
            if url.scheme!='https' or url.netloc!='farugby.matchready.es' or not route or url.query or url.fragment:
                raise ValueError('URL invalida')
            old=get('actas',{'partido_id':'eq.'+str(match['id']),'select':'id,hash_contenido,version'})
            if len(old)>1:
                raise ValueError('Acta duplicada')
            if not old:
                counts['nueva']+=1
                continue
            with opener.open(match['url_acta'],timeout=45) as response:
                raw=response.read(15000001)
            if len(raw)>15000000 or not raw.startswith(b'%PDF-'):
                raise ValueError('PDF invalido')
            reader=PdfReader(io.BytesIO(raw))
            if reader.is_encrypted or not 1<=len(reader.pages)<=50:
                raise ValueError('Paginas invalidas')
            content='\n'.join(p.extract_text() or '' for p in reader.pages)
            if not content.strip():
                raise ValueError('PDF vacio')
            digest=hashlib.sha256(' '.join(content.split()).encode()).hexdigest()
            if old[0]['hash_contenido']==digest:
                counts['sin_cambios']+=1
                continue
            counts['modificada']+=1
            if not players:
                for p in get('jugadores',{'select':'id,licencia,nombre_completo'}):
                    licence=str(p['licencia'])
                    if licence in players:
                        raise ValueError('Licencia ambigua')
                    players[licence]=p
            pack=build_manifest(raw,content,match,teams,players,route.group(1),digest)
            args=dict(p_acta=pack,p_version_esperada=old[0]['version'],p_hash_anterior=old[0]['hash_contenido'],p_simular=True)
            expected=dict(estado='corregido',version=old[0]['version']+1,registros={k:len(pack[k]) for k in ('alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal')},simulacion=True,escritura=False,version_anterior_conservada=True)
            if rpc('corregir_acta_estadisticas_m14',args)!=expected:
                raise ValueError('Simulacion discrepante')
            counts['simulada']+=1
            if get('actas',{'partido_id':'eq.'+str(match['id']),'select':'id,hash_contenido,version'})!=old:
                raise ValueError('Simulacion altera version')
            if apply:
                args['p_simular']=False
                saved=rpc('corregir_acta_estadisticas_m14',args)
                if saved!=dict(expected,simulacion=False,escritura=True):
                    raise ValueError('Guardado discrepante')
                repeated=rpc('corregir_acta_estadisticas_m14',args)
                if repeated!=dict(estado='existente',version=expected['version'],simulacion=False,escritura=False):
                    raise ValueError('Repeticion discrepante')
                stored=get('actas',{'partido_id':'eq.'+str(match['id']),'select':'id,hash_contenido,version'})
                if stored!=[dict(old[0],hash_contenido=digest,version=expected['version'])]:
                    raise ValueError('Version guardada discrepante')
                counts['corregida']+=1
                # Refresh identities before a second corrected acta introduces the same player.
                players={}
            print('CORRECCION 124',match['id'],'version',expected['version'],'guardada',apply,flush=True)
        except Exception as error:
            counts['error']+=1
            code=''
            if isinstance(error,urllib.error.HTTPError):
                try:
                    code=json.loads(error.read()).get('code','')
                except Exception:
                    pass
            print('REVISION 124',match['id'],type(error).__name__,code,flush=True)
    print('CORRECCIONES 124',json.dumps(dict(partidos=len(matches),autorizado=apply,registros=dict(counts)),sort_keys=True),flush=True)
    if counts['error']:
        raise SystemExit('Correcciones pendientes de revision')
if __name__=='__main__':
    main()
