begin;
create or replace function public.corregir_acta_estadisticas_m14(p_acta jsonb,p_version_esperada integer,p_hash_anterior text,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; p record; v_acta_actual record; v jsonb; j record; jid bigint; al bigint; en bigint; sa bigint; team bigint; v_dorsal integer; mins integer; pen integer; k text; result jsonb;
 marcador jsonb; n integer; counts jsonb:='{}'; previous jsonb; players jsonb; teams jsonb;
begin
 if p_simular is null or p_version_esperada is null or p_version_esperada<1 or coalesce(p_hash_anterior,'') !~ '^[0-9a-f]{64}$' then raise exception 'Modo o version invalida'; end if;
 if jsonb_typeof(p_acta) is distinct from 'object' then raise exception 'Paquete inválido'; end if;
 pid:=(p_acta->>'partido')::bigint;
 if pid is null or coalesce(p_acta->>'acta','') !~ '^[0-9]+$' or coalesce(p_acta->>'hash_contenido','') !~ '^[0-9a-f]{64}$' then raise exception 'Identificador o huella inválidos'; end if;
 if p_acta->>'url' is distinct from ('https://farugby.matchready.es/es/public/competition/match_pdf/'||(p_acta->>'acta')||'/act/') then raise exception 'URL inválida'; end if;
 marcador:=p_acta->'marcador';
 if jsonb_typeof(marcador) is distinct from 'array' then raise exception 'Marcador inválido'; end if;
 if jsonb_array_length(marcador)<>2 or coalesce(marcador->>0,'') !~ '^[0-9]+$' or coalesce(marcador->>1,'') !~ '^[0-9]+$' then raise exception 'Marcador inválido'; end if;

 foreach k in array array['alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal'] loop
  if jsonb_typeof(p_acta->k) is distinct from 'array' then raise exception 'Sección inválida %',k; end if;
  n:=jsonb_array_length(p_acta->k);
  if n>500 then raise exception 'Sección excesiva'; end if;
  counts:=counts||jsonb_build_object(k,n);
 end loop;
 if (counts->>'alineaciones')::int=0 or counts->>'alineaciones' is distinct from counts->>'participaciones' then raise exception 'Cobertura inválida'; end if;

 lock table competiciones,equipos,jornadas,partidos,actas,actas_versiones,jugadores,jugadores_equipos,alineaciones,anotaciones,sustituciones,sanciones,participaciones,personal_partidos,estadisticas_jugadores,estadisticas_equipos in share row exclusive mode;
 select p0.* into strict p from partidos p0 join jornadas jo on jo.id=p0.jornada_id join competiciones c on c.id=jo.competicion_id where p0.id=pid and c.id_oficial in ('35/11','35/12','35/15');
 select * into strict v_acta_actual from actas where partido_id=pid;
 if v_acta_actual.url_pdf is distinct from p_acta->>'url' or p.url_acta is distinct from v_acta_actual.url_pdf then raise exception 'Origen de correccion discrepante'; end if;
 if v_acta_actual.hash_contenido=p_acta->>'hash_contenido' then
  if v_acta_actual.datos is distinct from p_acta then raise exception 'Misma huella con datos diferentes'; end if;
  return jsonb_build_object('estado','existente','version',v_acta_actual.version,'simulacion',p_simular,'escritura',false);
 end if;
 if v_acta_actual.version is distinct from p_version_esperada or v_acta_actual.hash_contenido is distinct from p_hash_anterior then raise exception 'Conflicto de version'; end if;
 -- Reject every untrusted reference before writing.
 foreach k in array array['alineaciones','anotaciones','sustituciones','sanciones','participaciones'] loop
  for v in select value from jsonb_array_elements(p_acta->k) loop
   team:=(v->>'equipo_id')::bigint;
   if team is null or team not in (p.equipo_local_id,p.equipo_visitante_id) then raise exception 'Equipo fuera del partido'; end if;
  end loop;
 end loop;
 if exists(select 1 from jsonb_array_elements(p_acta->'alineaciones') as z(value) group by z.value->>'equipo_id',z.value->>'dorsal' having count(*)<>1)
 or exists(select 1 from jsonb_array_elements(p_acta->'alineaciones') as z(value) group by z.value->>'licencia' having count(*)<>1)
 then raise exception 'Alineacion duplicada'; end if;
 for v in select value from jsonb_array_elements(p_acta->'alineaciones') loop
  if coalesce(v->>'licencia','') !~ '^[0-9]{7}$' or coalesce(btrim(v->>'nombre_completo'),'')='' or coalesce((v->>'dorsal')::int,0)<1 or jsonb_typeof(v->'titular') is distinct from 'boolean' then raise exception 'Identidad invalida'; end if;
  select * into j from jugadores where licencia=v->>'licencia';
  if found and (v->>'jugador_id')::bigint is distinct from j.id then raise exception 'Identidad discrepante'; end if;
  if not found and v->>'jugador_id' is not null then raise exception 'Identidad desconocida'; end if;
 end loop;
 for team in select unnest(array[p.equipo_local_id,p.equipo_visitante_id]) loop
  if not exists(select 1 from jsonb_array_elements(p_acta->'alineaciones') as z(value) where (z.value->>'equipo_id')::bigint=team) then raise exception 'Equipo sin alineacion'; end if;
  if coalesce((select sum((z.value->>'puntos')::int) from jsonb_array_elements(p_acta->'anotaciones') as z(value) where (z.value->>'equipo_id')::bigint=team),0)<>(p_acta->'marcador'->>(case when team=p.equipo_local_id then 0 else 1 end))::int then raise exception 'Marcador no conciliado'; end if;
 end loop;

 begin
  previous:=jsonb_build_object('acta',to_jsonb(v_acta_actual),'partido',to_jsonb(p),
   'alineaciones',(select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) from alineaciones t where t.partido_id=pid),
   'participaciones',(select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) from participaciones t join alineaciones b on b.id=t.alineacion_id where b.partido_id=pid),
   'anotaciones',(select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) from anotaciones t where t.partido_id=pid),
   'sustituciones',(select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) from sustituciones t where t.partido_id=pid),
   'sanciones',(select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) from sanciones t where t.partido_id=pid),
   'personal',(select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) from personal_partidos t where t.partido_id=pid));
  if exists(select 1 from actas_versiones where acta_id=v_acta_actual.id and version=v_acta_actual.version and hash_contenido is distinct from v_acta_actual.hash_contenido) then raise exception 'Historial de version discrepante'; end if;
  insert into actas_versiones(acta_id,version,hash_contenido,datos,motivo)
  values(v_acta_actual.id,v_acta_actual.version,v_acta_actual.hash_contenido,jsonb_build_object('snapshot_previo',previous),'Conservacion anterior a correccion')
  on conflict(acta_id,version) do update set datos=actas_versiones.datos||excluded.datos
  where not (actas_versiones.datos ? 'snapshot_previo');
  delete from participaciones where alineacion_id in(select id from alineaciones where partido_id=pid);
  delete from anotaciones where partido_id=pid;
  delete from sustituciones where partido_id=pid;
  delete from sanciones where partido_id=pid;
  delete from personal_partidos where partido_id=pid;
  delete from alineaciones where partido_id=pid;
  for v in select value from jsonb_array_elements(p_acta->'alineaciones') loop
   select id into jid from jugadores where licencia=v->>'licencia';
   if jid is null then insert into jugadores(licencia,nombre_completo) values(v->>'licencia',v->>'nombre_completo') returning id into jid; end if;
   team:=(v->>'equipo_id')::bigint;
   if not exists(select 1 from jugadores_equipos where jugador_id=jid and equipo_id=team) then insert into jugadores_equipos(jugador_id,equipo_id) values(jid,team); end if;
   insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,team,jid,(v->>'dorsal')::int,(v->>'titular')::boolean);
  end loop;
  foreach k in array array['anotaciones','sustituciones','sanciones','participaciones'] loop
   for v in select value from jsonb_array_elements(p_acta->k) loop
    team:=(v->>'equipo_id')::bigint; v_dorsal:=(v->>'dorsal')::int;
    jid:=null; al:=null;
    if k<>'sustituciones' and not (k='anotaciones' and v->>'tipo'='ensayo_castigo') then
     select id,jugador_id into strict al,jid from alineaciones where partido_id=p.id and equipo_id=team and alineaciones.dorsal=v_dorsal;
    end if;
    if k<>'participaciones' and (coalesce((v->>'minuto')::int,-1) not between 0 and 36 or coalesce((v->>'orden_evento')::int,0)<1) then raise exception 'Tiempo u orden invalido'; end if;
    if k='anotaciones' then
     if coalesce(v->>'tipo','') not in ('ensayo','transformacion','golpe_castigo','drop','ensayo_castigo') or (v->>'puntos')::int is distinct from (case v->>'tipo' when 'ensayo' then 5 when 'transformacion' then 2 when 'golpe_castigo' then 3 when 'drop' then 3 when 'ensayo_castigo' then 7 end) or (v->>'tipo'='ensayo_castigo' and v_dorsal is not null) then raise exception 'Anotacion invalida'; end if;
     insert into anotaciones(partido_id,equipo_id,jugador_id,dorsal,minuto,orden_evento,tipo,puntos,verificada) values(p.id,team,jid,v_dorsal,(v->>'minuto')::int,(v->>'orden_evento')::int,v->>'tipo',(v->>'puntos')::int,true);
    elsif k='sustituciones' then
     select jugador_id into strict en from alineaciones where partido_id=p.id and equipo_id=team and alineaciones.dorsal=(v->>'dorsal_entra')::int;
     select jugador_id into strict sa from alineaciones where partido_id=p.id and equipo_id=team and alineaciones.dorsal=(v->>'dorsal_sale')::int;
     if en=sa then raise exception 'Cambio invalido'; end if;
     insert into sustituciones(partido_id,equipo_id,jugador_entra_id,jugador_sale_id,dorsal_entra,dorsal_sale,minuto,orden_evento,verificada) values(p.id,team,en,sa,(v->>'dorsal_entra')::int,(v->>'dorsal_sale')::int,(v->>'minuto')::int,(v->>'orden_evento')::int,true);
    elsif k='sanciones' then
     if coalesce(v->>'tipo','') not in ('temporal','definitiva') then raise exception 'Sancion invalida'; end if;
     insert into sanciones(partido_id,equipo_id,jugador_id,dorsal,minuto,orden_evento,tipo,duracion_reglamentaria,minuto_fin,verificada) values(p.id,team,jid,v_dorsal,(v->>'minuto')::int,(v->>'orden_evento')::int,v->>'tipo',case when v->>'tipo'='temporal' then 5 end,case when v->>'tipo'='temporal' then least(36,(v->>'minuto')::int+5) end,true);
    else
     mins:=(v->>'minutos_jugados')::int; pen:=(v->>'minutos_sancionado')::int;
     if mins is null or pen is null or mins<0 or pen<0 or mins+pen>36 then raise exception 'Minutos invalidos'; end if;
     insert into participaciones(alineacion_id,jugado,minutos_jugados,minutos_sancionado,estado_calculo) values(al,mins>0,mins,pen,'reglamentario');
    end if;
   end loop;
  end loop;
  if exists(select 1 from jsonb_array_elements(p_acta->'personal') as z(value) group by z.value->>'equipo_id',z.value->>'funcion' having count(*)>1) then raise exception 'Personal duplicado'; end if;
  for v in select value from jsonb_array_elements(p_acta->'personal') loop
   team:=(v->>'equipo_id')::bigint;
   if (team is not null and team not in (p.equipo_local_id,p.equipo_visitante_id)) or coalesce(v->>'funcion','') not in ('Entrenador','Delegado','Juez línea','Árbitro principal','Delegado de campo','Médico') or coalesce(btrim(v->>'licencia'),'')='' or (team is null) is distinct from (v->>'funcion' in ('Árbitro principal','Delegado de campo','Médico')) or (coalesce(btrim(v->>'nombre_completo'),'')='' and v->>'funcion'<>'Juez línea') then raise exception 'Personal invalido'; end if;
   insert into personal_partidos(partido_id,equipo_id,nombre_completo,licencia,funcion) values(p.id,team,v->>'nombre_completo',v->>'licencia',v->>'funcion');
  end loop;
  if (select count(*) from participaciones t join alineaciones b on b.id=t.alineacion_id where b.partido_id=p.id)<>jsonb_array_length(p_acta->'alineaciones') then raise exception 'Participaciones incompletas'; end if;

  update actas set hash_contenido=p_acta->>'hash_contenido',version=v_acta_actual.version+1,datos=p_acta,estado_validacion='pendiente',
   version_procesador='122',descargada_en=now(),comprobada_en=now(),actualizada_en=now() where id=v_acta_actual.id;
  insert into actas_versiones(acta_id,version,hash_contenido,datos,motivo)
  values(v_acta_actual.id,v_acta_actual.version+1,p_acta->>'hash_contenido',p_acta,'Correccion integral de contenido oficial');
  update partidos set puntos_local=(marcador->>0)::int,puntos_visitante=(marcador->>1)::int,estado='finalizado' where id=pid;
  players:=recalcular_jugadores_m14(false);
  teams:=recalcular_equipos_m14(false);
  if recalcular_jugadores_m14(true)->'discrepancias' is distinct from '0'::jsonb or recalcular_equipos_m14(true)->'discrepancias' is distinct from '0'::jsonb then raise exception 'Estadisticas corregidas no conciliadas'; end if;
  result:=jsonb_build_object('estado','corregido','version',v_acta_actual.version+1,'registros',counts,'simulacion',p_simular,'escritura',not p_simular,'version_anterior_conservada',true);
  if p_simular then raise exception using errcode='ZX122',message='Reversion de correccion y estadisticas'; end if;
 exception when sqlstate 'ZX122' then
  if not p_simular then raise; end if;
 end;
 return result;
end; $$;
revoke all on function public.corregir_acta_estadisticas_m14(jsonb,integer,text,boolean) from public,anon,authenticated;
grant execute on function public.corregir_acta_estadisticas_m14(jsonb,integer,text,boolean) to service_role;
commit;
