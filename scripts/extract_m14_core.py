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


def extract_events(raw,m,core):
    roster=core['roster']
    starting=core['starting']
    timeline={'local':[],'visitante':[]}
    participation={}
    with pdfplumber.open(io.BytesIO(raw)) as doc:
        for heading,allowed,fields in [('Cambios',{'CT','CL','CS','CC','C1','CJ','C20'},['dorsal entra','dorsal sale']),('Expulsiones temporales',set('ABCDE'),['dorsal']),('Expulsiones definitivas',set('ABCDE'),['dorsal'])]:
            found=set()
            for page in doc.pages:
                region=page
                if heading!='Cambios':
                    region=page.crop((0 if heading.endswith('temporales') else page.width/2,0,page.width/2 if heading.endswith('temporales') else page.width,page.height))
                words=region.extract_words(x_tolerance=2,y_tolerance=2)
                rows=[]
                for w in sorted(words,key=lambda w:(w['top'],w['x0'])):
                    if not rows or abs(rows[-1][0]['top']-w['top'])>=2: rows.append([w])
                    else: rows[-1].append(w)
                rows=[sorted(row,key=lambda w:w['x0']) for row in rows]
                lines=[' '.join(w['text'] for w in row).strip() for row in rows]
                starts=[i for i,line in enumerate(lines) if line.casefold()==heading.casefold()]
                for start in starts:
                    end=next((i for i in range(start+1,len(lines)) if re.fullmatch(r'Cambios|Expulsiones temporales|Expulsiones definitivas|Árbitro',lines[i],re.I)),len(lines))
                    teams=[i for i in range(start+1,end) if re.match(r'Equipo\s+(local|visitante):',lines[i],re.I)]
                    for k,idx in enumerate(teams):
                        side=re.match(r'Equipo\s+(local|visitante):',lines[idx],re.I).group(1).lower()
                        if side in found: continue
                        stop=teams[k+1] if k+1<len(teams) else end
                        labels={}
                        for i in range(idx+1,stop):
                            match=re.match(r'^(Dorsal entra|Dorsal sale|Dorsal|Minuto|Tipo)\b',lines[i],re.I)
                            if match: labels[match.group(1).lower()]=rows[i]
                        if not all(x in labels for x in ['tipo','minuto']+fields): continue
                        found.add(side)
                        def center(w): return (w['x0']+w['x1'])/2
                        def cell(label,x):
                            candidates=[w for w in labels[label] if w['text'].isdigit() and abs(center(w)-x)<12.5]
                            return int(min(candidates,key=lambda w:abs(center(w)-x))['text']) if candidates else None
                        events=[w for w in labels['tipo'] if w['text'].upper() in allowed]
                        unknown=[w for w in labels['tipo'] if w['text'].upper() not in allowed and w['text'].lower() not in ('tipo','*','tipo*')]
                        invalid=0
                        missing_minute=0; unknown_dorsal=0; same_dorsal=0
                        for event in events:
                            x=center(event)
                            minute=cell('minuto',x)
                            dorsals=[cell(label,x) for label in fields]
                            bad_minute=minute is None
                            bad_dorsal=any(d is None or d not in roster.get(side,set()) for d in dorsals)
                            bad_same=len(dorsals)==2 and dorsals[0]==dorsals[1]
                            missing_minute+=bad_minute; unknown_dorsal+=bad_dorsal; same_dorsal+=bad_same
                            if bad_minute or bad_dorsal or bad_same: invalid+=1
                            else: timeline[side].append((minute,heading,dorsals,event['text'].upper()))
                        print('Validación eventos',m['id'],heading,side,'minutos ausentes',missing_minute,'dorsales inválidos',unknown_dorsal,'entra igual sale',same_dorsal)
                        print(heading,side,'eventos',len(events),'incompletos',invalid,'tokens desconocidos',len(unknown))
            if found!={'local','visitante'}: print(heading,'bloques incompletos',len(found))
    # Compare simultaneous events by shared dorsals; independent events commute.
    occupancy={}
    sanction_minutes={}
    returns=0
    for side in ('local','visitante'):
        active=set(starting[side]); banned=set(); suspended={}; minutes={d:0 for d in roster[side]}
        sanctioned=Counter()
        issues=[]; compatible=0; ambiguous=0; counts=[]
        events=sorted(timeline[side],key=lambda e:e[0])
        for t in range(37):
            returning={d for d,end in suspended.items() if end==t}
            for d in returning:
                del suspended[d]
                if d not in banned:
                    active.add(d)
                    returns+=int(t<36)
            current=[e for e in events if e[0]==t]
            touched=[d for e in current for d in e[2]]
            if len(touched)!=len(set(touched)) or returning.intersection(touched):
                ambiguous+=1; issues.append('orden ambiguo')
            elif len(current)>1: compatible+=1
            for _,kind,ds,code in current:
                if kind=='Cambios':
                    enter,leave=ds
                    if leave not in active or enter in active or enter in banned or enter in suspended: issues.append('cambio incompatible')
                    else: active.remove(leave); active.add(enter)
                else:
                    d=ds[0]
                    if d not in active: issues.append('sanción fuera de campo')
                    active.discard(d)
                    if kind=='Expulsiones definitivas': banned.add(d); suspended.pop(d,None)
                    else: suspended[d]=t+5
            if len(active)>15: issues.append('exceso jugadores')
            if t<36:
                counts.append(len(active))
                for d in active: minutes[d]+=1
                sanctioned.update(set(suspended)|banned)
        if any(e[0]<0 or e[0]>36 for e in events): issues.append('minuto fuera duración')
        if sum(minutes.values())!=sum(counts) or any(v<0 or v>36 for v in minutes.values()): issues.append('balance minutos')
        participation[side]=(minutes,not issues)
        occupancy[side]=counts
        sanction_minutes[side]={d:sanctioned[d] for d in roster[side]}
        if any(minutes[d]+sanctioned[d]>36 for d in roster[side]):raise ValueError('Balance individual de sanción')
        print('Simulación participación',m['id'],side,'titulares',len(starting[side]),'jugadores con minutos',sum(v>0 for v in minutes.values()),'minutos acumulados',sum(minutes.values()),'simultáneos compatibles',compatible,'ambiguos',ambiguous,'incidencias',len(issues),'tipos',','.join(sorted(set(issues))) or 'ninguna')
    differences=[a-b for a,b in zip(occupancy['local'],occupancy['visitante'])]
    equal=sum(d==0 for d in differences); superior=sum(d>0 for d in differences); inferior=sum(d<0 for d in differences)
    print('Balance numérico',m['id'],'igualdad',equal,'superior local',superior,'superior visitante',inferior,'minutos diferencia local',sum(max(0,d) for d in differences),'minutos diferencia visitante',sum(max(0,-d) for d in differences),'coincide',equal+superior+inferior==36)
    return dict(timeline=timeline,participation=participation,occupancy=occupancy,equal=equal,superior=superior,inferior=inferior,sanction_minutes=sanction_minutes,returns=returns)


def extract_staff(raw,m,core):
    import json
    identities=core['identities']
    staff58=[]
    staff_slots58=Counter()
    staff_ids=set()
    with pdfplumber.open(io.BytesIO(raw)) as doc:
        page=doc.pages[0]
        for side in (0,1):
            region=page.crop((side*page.width/2,0,(side+1)*page.width/2,page.height))
            lines=(region.extract_text(x_tolerance=3,y_tolerance=2) or '').splitlines()
            for role in ('Entrenador','Delegado','Juez línea'):
                hits=[]
                for i,line in enumerate(lines):
                    match=re.match(r'^\s*'+re.escape(role)+r'\s*:\s*(.*)$',line,re.I)
                    if not match: continue
                    name=match.group(1).strip()
                    licence=re.search(r'(?i)N[ºo]\s*licencia\s*:\s*('+ (r'\d{6,7}' if role=='Juez línea' else r'\d{7}') +r')\b',' '.join(lines[i+1:i+4]).split('Entrenador:')[0].split('Delegado:')[0].split('Juez línea:')[0])
                    if name and licence and any(c.isalpha() for c in name):
                        hits.append((licence.group(1),name))
                if role=='Juez línea' and not hits:
                    for i,line in enumerate(lines):
                        short=re.fullmatch(r'\s*Juez\s+l[ií]nea\s*:\s*(\d{6})\s*',line,re.I)
                        if not short: continue
                        lic=re.search(r'(?i)N[ºo]\s*licencia\s*:\s*(\d{6})\b',' '.join(lines[i+1:i+4]))
                        if lic and lic.group(1)==short.group(1):
                            side61='local' if side==0 else 'visitante'
                            staff58.append(dict(partido_id=m['id'],equipo_id=m['equipo_'+side61+'_id'],funcion=role,licencia=lic.group(1),nombre_completo=None,en_alineacion=False))
                            print('JUEZ 61 identificador repetido sin nombre',m['id'],side61,'coincide_alineacion',lic.group(1) in identities[side61].values())
                staff_slots58[(role,'completo' if len(hits)==1 else 'ausente_o_ambiguo')]+=1
                if len(hits)==1:
                    ident58,name58=hits[0]
                    staff_ids.add(ident58)
                    side58='local' if side==0 else 'visitante'
                    staff58.append(dict(partido_id=m['id'],equipo_id=m['equipo_'+side58+'_id'],funcion=role,licencia=ident58,nombre_completo=name58,en_alineacion=ident58 in identities[side58].values()))
                if role=='Juez línea' and not hits:
                    labels=[i for i,line in enumerate(lines) if re.search(r'(?i)juez\s+l[ií]nea',line)]
                    for idx in labels:
                        segment=' '.join(lines[idx:idx+3])
                        # Inspect geometry only; never print names or identifiers.
                        halfwords=region.extract_words(x_tolerance=2,y_tolerance=2)
                        labels60=[w for w in halfwords if w['text'].casefold().strip(':')=='juez']
                        for label60 in labels60:
                            y60=label60['top']
                            band60=[w for w in halfwords if y60-2<=w['top']<=y60+24]
                            shape60=[]
                            for w in sorted(band60,key=lambda w:(round(w['top']/2),w['x0'])):
                                t=w['text'].casefold().strip(':')
                                kind=t if t in ('juez','línea','linea','nº','no','licencia','delegado','entrenador') else 'NUM'+str(len(t)) if t.isdigit() else 'VACIO' if not any(c.isalnum() for c in t) else 'TEXTO'
                                shape60.append([round(w['top']-y60,1),round((w['x0']-side*page.width/2)/page.width,3),kind])
                            print('JUEZ 60 geometría',m['id'],'lado',side,json.dumps(shape60,ensure_ascii=False))

                        print('REVISION JUEZ 59',m['id'],'lado',side,'campo_vacio',not bool(re.sub(r'(?i)juez\\s+l[ií]nea\\s*:?|n[ºo]\\s*licencia\\s*:?|[\\s:–—-]','',segment)),'lineas',len(lines[idx:idx+3]))
                        print('Diagnóstico juez',m['id'],'lado',side,'etiqueta presente',True,'licencia presente',bool(re.search(r'\b\d{7}\b',segment)),'nombre presente',bool(re.search(r'(?i)juez\s+l[ií]nea\s*:\s*[A-Za-zÁÉÍÓÚÑáéíóúñ]',lines[idx])))
                    if not labels: print('Diagnóstico juez',m['id'],'lado',side,'etiqueta presente',False)
                print('Personal',role,'local' if side==0 else 'visitante','registros completos',len(hits))
        words=page.extract_words(x_tolerance=2,y_tolerance=2)
        referee_id=None
        officials={}
        for h in words:
            if h['text'].casefold()!='árbitro' or h['top']<page.height*.7: continue
            header=page.crop((0,max(0,h['top']-4),page.width,min(page.height,h['bottom']+4))).extract_text() or ''
            if not all(t in header.casefold() for t in ('delegado','médico','licencia')): continue
            headerwords=[w for w in words if abs(w['top']-h['top'])<4]
            headerwords.sort(key=lambda w:w['x0'])
            groups=[]
            for w in headerwords:
                if not groups or w['x0']-groups[-1][-1]['x1']>12: groups.append([w])
                else: groups[-1].append(w)
            labels=[' '.join(w['text'] for w in g).casefold() for g in groups]
            centers=[(g[0]['x0']+g[-1]['x1'])/2 for g in groups]
            print('Encabezados personal','seis' if len(groups)==6 else 'variante','cantidad',len(groups))
            edges=[0]+[(x+y)/2 for x,y in zip(centers,centers[1:])]+[page.width] if len(groups)==6 else [page.width*i/6 for i in range(7)]
            for i,role in enumerate(('Árbitro principal','Delegado de campo','Médico')):
                namebox=page.crop((edges[2*i],h['bottom']+1,edges[2*i+1],min(page.height,h['bottom']+40)))
                idbox=page.crop((edges[2*i+1],h['bottom']+1,edges[2*i+2],min(page.height,h['bottom']+40)))
                name=' '.join((namebox.extract_text() or '').split())
                ident=' '.join((idbox.extract_text() or '').split())
                ids=re.findall(r'\b\d{3,10}\b',ident)
                has_name=any(c.isalpha() for c in name) and name.casefold() not in ('no','ninguno','sin médico','no consta')
                # Preserve the entire printed identifier, including prefixes.
                if i==2:
                    if not has_name and not ident.strip(' -–—'):
                        print('Estado médico ausente en origen')
                    elif has_name and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9 ./-]{0,30}',ident) and any(c.isdigit() for c in ident):
                        officials[role]=ident
                        staff58.append(dict(partido_id=m['id'],equipo_id=None,funcion=role,licencia=ident,nombre_completo=name,en_alineacion=False))
                        staff_ids.update(ids)
                        print('Estado médico identificación conservada')
                    else:
                        print('Estado médico revisión manual')
                if i!=2 and len(ids)==1 and has_name and len(ids[0])==7:
                    officials[role]=ids[0]
                    staff58.append(dict(partido_id=m['id'],equipo_id=None,funcion=role,licencia=ids[0],nombre_completo=name,en_alineacion=False))
                    staff_ids.add(ids[0])
                if i==2:
                    print('Diagnóstico médico','nombre presente',has_name,'campo identificación vacío',not ident or not ident.strip('-–—'),'cantidad números',len(ids),'longitudes',','.join(str(len(x)) for x in ids),'texto no numérico',any(c.isalpha() for c in ident))
            break
        print('Formato seis columnas reconocido',len(officials)==3)
        referee_id=officials.get('Árbitro principal')
        for role in ('Delegado de campo','Médico'):
            print(role,'registro completo',role in officials)
    return staff58
