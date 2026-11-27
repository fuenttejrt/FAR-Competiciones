begin;
-- 113: a validated manifest is the sole source of every inserted section.
create or replace function public.importar_acta_integral_m14(p_acta jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare gate jsonb; p record; v jsonb; j record; jid bigint; aid bigint; al bigint; en bigint; sa bigint; team bigint; v_dorsal integer; mins integer; pen integer; k text; result jsonb;
begin
 if p_simular is null then raise exception 'Modo obligatorio'; end if;
 lock table jornadas,partidos,actas,actas_versiones,jugadores,jugadores_equipos,alineaciones,anotaciones,sustituciones,sanciones,participaciones,personal_partidos in share row exclusive mode;
 gate:=prevalidar_importacion_acta_m14(p_acta);
 if gate->>'estado'='existente' then
   return jsonb_build_object('estado','existente','escritura',false,'simulacion',p_simular);
 end if;
 select * into strict p from partidos where id=(p_acta->>'partido')::bigint;
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
  if (select count(*) from participaciones t join alineaciones a on a.id=t.alineacion_id where a.partido_id=p.id)<>jsonb_array_length(p_acta->'alineaciones') then raise exception 'Participaciones incompletas'; end if;
  insert into actas(partido_id,url_pdf,hash_contenido,version,datos,estado_validacion,descargada_en,comprobada_en,version_procesador) values(p.id,p_acta->>'url',p_acta->>'hash_contenido',1,p_acta,'pendiente',now(),now(),'113') returning id into aid;
  insert into actas_versiones(acta_id,version,hash_contenido,datos,motivo) values(aid,1,p_acta->>'hash_contenido',p_acta,'Importacion integral inicial');
  update partidos set puntos_local=(p_acta->'marcador'->>0)::int,puntos_visitante=(p_acta->'marcador'->>1)::int,estado='finalizado',url_acta=p_acta->>'url' where id=p.id;
  result:=jsonb_build_object('estado','importado','registros',gate->'registros','simulacion',p_simular,'escritura',not p_simular);
  if p_simular then raise exception using errcode='ZX113',message='Reversion de simulacion'; end if;
 exception when sqlstate 'ZX113' then
  if not p_simular then raise; end if;
 end;
 return result;
end; $$;
revoke all on function public.importar_acta_integral_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_acta_integral_m14(jsonb,boolean) to service_role;
commit;
