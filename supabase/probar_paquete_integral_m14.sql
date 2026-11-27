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

begin;
create or replace function public.probar_paquete_integral_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare src record; dst record; payload jsonb; k text; t text; rows jsonb; before_counts jsonb:='{}'; after_counts jsonb:='{}'; n bigint; snapshot jsonb; result jsonb; repeated jsonb; invalid_payload jsonb; mode boolean; caught boolean; error_message text; saved jsonb; duplicate_result jsonb; first_snapshot jsonb:='{}'; second_snapshot jsonb:='{}'; digest text; stored_counts jsonb; rolled boolean:=false;
begin
 lock table jornadas,partidos,actas,actas_versiones,jugadores,jugadores_equipos,alineaciones,anotaciones,sustituciones,sanciones,participaciones,personal_partidos in share row exclusive mode;
 if prevalidar_importacion_acta_m14(p_acta)->>'estado' is distinct from 'existente' then raise exception 'Origen no confirmado'; end if;
 select * into strict src from partidos where id=(p_acta->>'partido')::bigint;
 select q.* into dst from partidos q join jornadas j on j.id=q.jornada_id join competiciones c on c.id=j.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and q.estado='pendiente' and q.puntos_local is null and q.puntos_visitante is null and q.url_acta is null
 and not exists(select 1 from actas x where x.partido_id=q.id)
 and not exists(select 1 from alineaciones x where x.partido_id=q.id)
 and not exists(select 1 from anotaciones x where x.partido_id=q.id)
 and not exists(select 1 from sustituciones x where x.partido_id=q.id)
 and not exists(select 1 from sanciones x where x.partido_id=q.id)
 and not exists(select 1 from personal_partidos x where x.partido_id=q.id)
 order by q.id limit 1;
 if not found then raise exception 'Sin partido vacio para simulacion'; end if;
 snapshot:=to_jsonb(dst);
 foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos'] loop
  execute format('select count(*) from public.%I',t) into n;
  before_counts:=before_counts||jsonb_build_object(t,n);
 end loop;
 payload:=jsonb_set(p_acta,'{partido}',to_jsonb(dst.id));
 foreach k in array array['alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal'] loop
  select coalesce(jsonb_agg(case when v->>'equipo_id' is null then v else jsonb_set(v,'{equipo_id}',to_jsonb(case (v->>'equipo_id')::bigint when src.equipo_local_id then dst.equipo_local_id when src.equipo_visitante_id then dst.equipo_visitante_id end)) end order by ord),'[]'::jsonb)
  into rows from jsonb_array_elements(p_acta->k) with ordinality as e(v,ord);
  payload:=jsonb_set(payload,array[k],rows);
 end loop;
 result:=importar_acta_integral_m14(payload,true);
 repeated:=importar_acta_integral_m14(payload,true);
 if result is distinct from repeated or result->>'estado' is distinct from 'importado' or result->'simulacion' is distinct from 'true'::jsonb or result->'escritura' is distinct from 'false'::jsonb or result->'registros' is distinct from prevalidar_importacion_acta_m14(payload)->'registros' then raise exception 'Simulacion incompleta'; end if;

 -- 115: only the final staff record is changed; all earlier sections are real.
 if jsonb_array_length(payload->'personal')=0 then raise exception 'Sin personal para fallo final'; end if;
 invalid_payload:=jsonb_set(payload,array['personal',(jsonb_array_length(payload->'personal')-1)::text,'licencia'],'null'::jsonb);
 foreach mode in array array[true,false] loop
  caught:=false;
  begin
   perform importar_acta_integral_m14(invalid_payload,mode);
   -- Unexpected success aborts the entire RPC, including all writes.
   raise exception using errcode='ZX115',message='Paquete invalido aceptado';
  exception when sqlstate 'P0001' then
   get stacked diagnostics error_message=message_text;
   if error_message is distinct from 'Personal invalido' then raise; end if;
   caught:=true;
  end;
  if not caught then raise exception 'Fallo final no detectado'; end if;
  if exists(select 1 from alineaciones where partido_id=dst.id)
  or exists(select 1 from personal_partidos where partido_id=dst.id)
  or exists(select 1 from actas where partido_id=dst.id)
  then raise exception 'Escritura parcial tras fallo final'; end if;
 end loop;
 -- A valid retry must still work after both rejected imports.
 if importar_acta_integral_m14(payload,true) is distinct from result then raise exception 'Recuperacion tras fallo discrepante'; end if;

 -- 116: exercise the saving path, then repeat against the stored acta.
 begin
  saved:=importar_acta_integral_m14(payload,false);
  if saved->>'estado' is distinct from 'importado' or saved->'escritura' is distinct from 'true'::jsonb
  or saved->'registros' is distinct from result->'registros' then raise exception 'Guardado incompleto'; end if;
  select jsonb_build_object(
   'alineaciones',(select count(*) from alineaciones where partido_id=dst.id),
   'anotaciones',(select count(*) from anotaciones where partido_id=dst.id),
   'sustituciones',(select count(*) from sustituciones where partido_id=dst.id),
   'sanciones',(select count(*) from sanciones where partido_id=dst.id),
   'participaciones',(select count(*) from participaciones t join alineaciones a on a.id=t.alineacion_id where a.partido_id=dst.id),
   'personal',(select count(*) from personal_partidos where partido_id=dst.id)
  ) into stored_counts;
  if stored_counts is distinct from result->'registros'
  or not exists(select 1 from actas a join actas_versiones v on v.acta_id=a.id join partidos p on p.id=a.partido_id
    where p.id=dst.id and a.datos=payload and v.datos=payload
    and a.version=1 and v.version=1 and a.hash_contenido=payload->>'hash_contenido' and v.hash_contenido=a.hash_contenido
    and p.url_acta=payload->>'url' and p.estado='finalizado'
    and p.puntos_local=(payload->'marcador'->>0)::int and p.puntos_visitante=(payload->'marcador'->>1)::int)
  then raise exception 'Datos guardados no conciliados'; end if;
  foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos'] loop
   execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',t) into digest;
   first_snapshot:=first_snapshot||jsonb_build_object(t,digest);
  end loop;
  duplicate_result:=importar_acta_integral_m14(payload,false);
  if duplicate_result is distinct from '{"estado":"existente","escritura":false,"simulacion":false}'::jsonb then raise exception 'Repeticion no reconocida'; end if;
  foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos'] loop
   execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',t) into digest;
   second_snapshot:=second_snapshot||jsonb_build_object(t,digest);
  end loop;
  if first_snapshot is distinct from second_snapshot then raise exception 'Repeticion modifica registros'; end if;
  raise exception using errcode='ZX116',message='Reversion obligatoria del guardado de prueba';
 exception when sqlstate 'ZX116' then rolled:=true;
 end;
 if not rolled then raise exception 'Guardado de prueba no revertido'; end if;
 foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos'] loop
  execute format('select count(*) from public.%I',t) into n;
  after_counts:=after_counts||jsonb_build_object(t,n);
 end loop;
 if before_counts is distinct from after_counts or (select to_jsonb(q) from partidos q where q.id=dst.id) is distinct from snapshot
 or exists(select 1 from actas where partido_id=dst.id)
 or exists(select 1 from alineaciones where partido_id=dst.id)
 or exists(select 1 from anotaciones where partido_id=dst.id)
 or exists(select 1 from sustituciones where partido_id=dst.id)
 or exists(select 1 from sanciones where partido_id=dst.id)
 or exists(select 1 from personal_partidos where partido_id=dst.id)
 then raise exception 'Reversion no confirmada'; end if;
 return jsonb_build_object('registros',result->'registros','repeticion',true,'fallo_final_revertido',true,'modos_probados',2,'recuperacion',true,'guardado_completo',true,'repeticion_sin_cambios',true,'reversion',true,'tablas_verificadas',11,'escritura',false);
end; $$;
revoke all on function public.probar_paquete_integral_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_paquete_integral_m14(jsonb) to service_role;
commit;
