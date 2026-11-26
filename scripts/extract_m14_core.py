import io,re,hashlib
from collections import Counter,defaultdict
import pdfplumber

def extract_roster_scoring(raw,content,m):
    roster_audit=Counter()
    name_variants=defaultdict(set)
    with pdfplumber.open(io.BytesIO(raw)) as doc:
        page=doc.pages[0]
        words=page.extract_words(x_tolerance=3,y_tolerance=2)
        licences=set()
        roster={}
        identities={}
        scores={}
        participation={}
        duration_warning=False
        score_ok={}
        starting={}
        timeline={'local':[],'visitante':[]}
        rejected={}
        staff_ids=set()
        for side in (0,1):
            col=[w for w in words if (w['x0']<page.width/2)==(side==0)]
            anchors=[w for w in col if re.fullmatch(r'\d{7}',w['text'])]
            headers=sorted(w['top'] for w in col if 'dorsal' in w['text'].casefold())
            parsed=[]; unmatched=0
            rejected[side]=[]
            for anchor in anchors:
                line=' '.join(w['text'] for w in sorted([w for w in col if abs(w['top']-anchor['top'])<2],key=lambda w:w['x0']))
                # Match the dorsal and licence by their physical columns, not the name's line.
                row=sorted([w for w in col if abs(w['top']-anchor['top'])<2],key=lambda w:w['x0'])
                offset=side*page.width/2
                dorsal=[w for w in row if w['x0']-offset<page.width*.075 and re.fullmatch(r'\d{1,3}',w['text'])]
                in_roster=bool(headers) and min(headers)<anchor['top']<page.height*.65
                licence_column=page.width*.38<anchor['x0']-offset<page.width*.48
                if not (in_roster and licence_column and len(dorsal)==1):
                    unmatched+=1; rejected[side].append(anchor['text']); continue
                namewords=[w for w in row if page.width*.085<w['x0']-offset<page.width*.35 and any(c.isalpha() for c in w['text'])]
                if not namewords:
                    # A wrapped name can sit above or below the dorsal baseline.
                    namewords=[w for w in col if abs(w['top']-anchor['top'])<12 and page.width*.085<w['x0']-offset<page.width*.35 and any(c.isalpha() for c in w['text'])]
                if not namewords:
                    unmatched+=1; rejected[side].append(anchor['text']); continue
                parsed.append((anchor['top'],int(dorsal[0]['text']),anchor['text']))
            identities['local' if side==0 else 'visitante']={x[1]:x[2] for x in parsed}
            roster['local' if side==0 else 'visitante']={x[1] for x in parsed}
            split=next((y for y in headers if parsed and y>min(x[0] for x in parsed)+2),None)
            starters=sum(x[0]<split for x in parsed) if split is not None else None
            starting['local' if side==0 else 'visitante']={x[1] for x in parsed if split is not None and x[0]<split}
            if starters!=15 or len(parsed)==starters:
                print('Diagnóstico plantilla reducida',m['id'],'lado',side,'titulares',starters,'total',len(parsed),'licencias columna plantilla',sum(min(headers,default=page.height)<w['top']<page.height*.65 and page.width*.38<w['x0']-side*page.width/2<page.width*.48 for w in anchors),'cabeceras dorsal',len(headers))
            # Compare geometric row reconstruction with baseline extraction.
            ordered=sorted(parsed)
            offset=side*page.width/2
            for hy in headers:
                for w in col:
                    if abs(w['top']-hy)<2:
                        label=w['text'].casefold().strip(':')
                        if label in ('dorsal','nombre','apellidos','jugador','licencia','c','x','f','capitán','capitan'):
                            roster_audit['cabecera_'+label+'_lado_'+str(side)+'_x_'+str(round((w['x0']-offset)/page.width,3))]+=1
            name_left=page.width*.075
            for idx,(y,d,ident) in enumerate(ordered):
                roster_audit['filas']+=1
                offset=side*page.width/2
                lo=(ordered[idx-1][0]+y)/2 if idx else y-12
                hi=(ordered[idx+1][0]+y)/2 if idx+1<len(ordered) else y+12
                for boundary in headers:
                    if lo<boundary<y: lo=boundary+2
                    if y<boundary<hi: hi=boundary-2
                nearby=[w for w in col if lo<=w['top']<hi]
                # Record only aggregate column geometry, never text or licence.
                for w in nearby:
                    if abs(w['top']-y)<2 and any(c.isalpha() for c in w['text']):
                        roster_audit['texto_x_'+str(round((w['x0']-offset)/page.width,2))]+=1
                tokens=[w for w in nearby if name_left<=w['x0']-offset<page.width*.38 and any(c.isalpha() for c in w['text']) and w['text'].upper() not in ('C','X','F')]
                lines=[]
                for w in sorted(tokens,key=lambda w:(w['top'],w['x0'])):
                    if not lines or abs(w['top']-lines[-1][0]['top'])>=2: lines.append([])
                    lines[-1].append(w)
                name=' '.join(w['text'] for line in lines for w in sorted(line,key=lambda w:w['x0']))
                roster_audit['nombre_palabras_'+str(min(len(name.split()),8))]+=1
                roster_audit['nombre_contiene_digitos']+=int(any(c.isdigit() for c in name))
                roster_audit['nombre_posible_cabecera']+=int(any(w.casefold().strip(':') in ('dorsal','licencia','jugador','titulares','suplentes','apellidos') for w in name.split()))
                if len(name.split())>=2:
                    roster_audit['nombre_multitoken']+=1
                    name_variants[ident].add(' '.join(name.casefold().split()))
                else: roster_audit['nombre_revisar']+=1
                if len(lines)>1: roster_audit['nombre_multilinea']+=1
                markers=[w for w in nearby if abs(w['top']-y)<2 and w['text'].upper() in ('C','X') and page.width*.035<w['x0']-offset<page.width*.12]
                for w in markers:
                    roster_audit['marca_'+w['text'].upper()+'_x_'+str(round((w['x0']-offset)/page.width,2))]+=1
            duplicates=len(parsed)-len({x[1] for x in parsed})
            print('Licencias repetidas dentro del equipo',len(parsed)-len({x[2] for x in parsed}))
            repeated=sum(x[2] in licences for x in parsed)
            licences.update(x[2] for x in parsed)
            print('Alineación', 'local' if side==0 else 'visitante','filas',len(parsed),'titulares',starters,'suplentes',len(parsed)-starters if starters is not None else None,'filas no reconocidas',unmatched,'dorsales repetidos',duplicates,'licencias cruzadas',repeated,'separación explícita',split is not None)
    content_hash=hashlib.sha256(' '.join(content.split()).encode()).hexdigest()
    print('Huella texto normalizado:',content_hash)
    weights={'E':5,'T':2,'D':3,'PC':3,'EC':7}
    sections=[]
    with pdfplumber.open(io.BytesIO(raw)) as doc:
        for page in doc.pages:
            region=page.crop((0,page.height*.13,page.width,page.height*.47))
            score=region.extract_text(x_tolerance=3,y_tolerance=2) or ''
            heading=re.search(r'(?im)^\s*Marcador\s*$',score)
            if heading:
                sections.append(re.split(r'(?im)^\s*(?:Cambios|Expulsiones temporales|Expulsiones definitivas)\s*$',score[heading.end():],maxsplit=1)[0])
    blocks=re.split(r'(?im)(?=^\s*Equipo\s+(?:local|visitante):)','\n'.join(sections))
    seen=set()
    for block in blocks:
        team=re.match(r'(?i)^\s*Equipo\s+(local|visitante):',block)
        if not team: continue
        side=team.group(1).lower()
        if side in seen:
            print('Marcador ambiguo: bloque repetido',side); continue
        seen.add(side)
        cols={}
        for line in block.splitlines():
            field=re.fullmatch(r'\s*(Tipo|Minuto|Dorsal)\s*\*?\s*(.*)',line,re.I)
            if field: cols[field.group(1).lower()]=field.group(2).split()
        types=[x.upper() for x in cols.get('tipo',[])]
        minutes=cols.get('minuto',[])
        dorsals=cols.get('dorsal',[])
        expected=m['puntos_local' if side=='local' else 'puntos_visitante']
        valid='tipo' in cols and 'minuto' in cols and len(types)==len(minutes) and all(x in weights for x in types) and all(x.isdigit() for x in minutes)
        # Collective scoring and individual attribution are independent checks.
        individual_expected=sum(t!='EC' for t in types)
        known_dorsals=sum(d.isdigit() and int(d) in roster.get(side,set()) for d in dorsals)
        print('Validación anotación individual',m['id'],side,'esperados',individual_expected,'dorsales reconocidos',known_dorsals,'tokens dorsal',len(dorsals),'tipos desconocidos',sum(t not in weights for t in types),'minutos inválidos',sum(not x.isdigit() for x in minutes))
        # Match physical scoring columns, including blank penalty-try dorsal cells.
        scoring_columns=[]
        with pdfplumber.open(io.BytesIO(raw)) as score_doc:
            for score_page in score_doc.pages:
                region=score_page.crop((0,score_page.height*.13,score_page.width,score_page.height*.47))
                words=region.extract_words(x_tolerance=2,y_tolerance=2)
                rows=[]
                for w in sorted(words,key=lambda w:(w['top'],w['x0'])):
                    if not rows or abs(rows[-1][0]['top']-w['top'])>=2: rows.append([w])
                    else: rows[-1].append(w)
                rows=[sorted(row,key=lambda w:w['x0']) for row in rows]
                labels=[' '.join(w['text'] for w in row) for row in rows]
                headings=[i for i,label in enumerate(labels) if label.strip().casefold()=='marcador']
                if not headings: continue
                first=headings[0]
                last=next((i for i in range(first+1,len(labels)) if re.match(r'(?i)^Cambios|^Expulsiones',labels[i])),len(labels))
                for i in range(first+1,last):
                    label=labels[i]
                    if not re.match(r'(?i)^Equipo\s+'+side+r':',label): continue
                    fields={}
                    for j in range(i+1,min(i+9,last)):
                        if re.match(r'(?i)^Equipo\s+|^Cambios|^Expulsiones',labels[j]): break
                        match=re.match(r'(?i)^(Tipo|Minuto|Dorsal)\b',labels[j])
                        if match: fields[match.group(1).lower()]=rows[j]
                    if not all(k in fields for k in ('tipo','minuto','dorsal')): continue
                    def cx(w): return (w['x0']+w['x1'])/2
                    def value(k,x):
                        candidates=[w for w in fields[k] if w['text'].isdigit() and abs(cx(w)-x)<12.5]
                        return int(min(candidates,key=lambda w:abs(cx(w)-x))['text']) if candidates else None
                    for w in fields['tipo']:
                        kind=w['text'].upper()
                        if kind in weights: scoring_columns.append((kind,value('minuto',cx(w)),value('dorsal',cx(w))))
        attribution_errors=sum(t is None or t<0 or (d is not None if k=='EC' else d not in roster.get(side,set())) for k,t,d in scoring_columns)
        temporal_warnings=sum(t is not None and t>36 for k,t,d in scoring_columns)
        exact=len(scoring_columns)==len(types) and [k for k,t,d in scoring_columns]==types and [str(t) for k,t,d in scoring_columns]==minutes and attribution_errors==0
        print('Atribución exacta',m['id'],side,'eventos',len(scoring_columns),'esperados',len(types),'errores',attribution_errors,'coincide',exact)
        print('Validación temporal anotaciones',m['id'],side,'advertencias',temporal_warnings,'duración referencia',36)
        scores[side]=scoring_columns
        score_ok[side]=exact and valid and sum(weights.get(x,0) for x in types)==expected
        # Scoring beyond regulation is retained; participation remains capped at 36.
        if temporal_warnings:
            print('Anotación fuera de referencia conservada',m['id'],side,'eventos',temporal_warnings,'tope estadístico',36)
        if temporal_warnings:
            print('Anotación original posterior a duración de referencia',m['id'],'minuto máximo anotación',max(t for k,t,d in scoring_columns if t is not None))
        total=sum(weights.get(x,0) for x in types)
        print('Marcador',side,'eventos',len(types),'ensayos',types.count('E'),'transformaciones',types.count('T'),'castigo',types.count('EC'),'golpes',types.count('PC'),'drops',types.count('D'),'puntos extraídos',total,'oficial',expected,'coincide',valid and total==expected,'dorsales disponibles',len(dorsals))
    if seen!={'local','visitante'}: print('Marcador incompleto: faltan bloques de equipo')
    return dict(roster=roster,identities=identities,starting=starting,scores=scores,score_ok=score_ok,content_hash=content_hash)
