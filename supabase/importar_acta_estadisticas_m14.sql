begin;
-- 120: acta and both statistics tables share one transaction and one rollback.
create or replace function public.importar_acta_estadisticas_m14(p_acta jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare imported jsonb; players jsonb; teams jsonb; result jsonb; check_players jsonb; check_teams jsonb;
begin
 if p_simular is null then raise exception 'Modo obligatorio'; end if;
 lock table competiciones,equipos,jornadas,partidos,actas,actas_versiones,jugadores,jugadores_equipos,alineaciones,anotaciones,sustituciones,sanciones,participaciones,personal_partidos,estadisticas_jugadores,estadisticas_equipos in share row exclusive mode;
 begin
  -- Execute the real saving path even for a simulation, then roll everything back.
  imported:=importar_acta_integral_m14(p_acta,false);
  if imported->>'estado' not in ('importado','existente') then raise exception 'Importacion incompleta'; end if;
  players:=recalcular_jugadores_m14(false);
  teams:=recalcular_equipos_m14(false);
  check_players:=recalcular_jugadores_m14(true);
  check_teams:=recalcular_equipos_m14(true);
  if check_players->'discrepancias' is distinct from '0'::jsonb
  or check_teams->'discrepancias' is distinct from '0'::jsonb then raise exception 'Estadisticas no conciliadas'; end if;
  result:=imported||jsonb_build_object('simulacion',p_simular,'escritura',not p_simular and (imported->>'estado'='importado' or (players->>'actualizados')::int+(players->>'insertados')::int+(teams->>'actualizados')::int+(teams->>'insertados')::int>0),
   'estadisticas_jugadores',players->'registros','estadisticas_equipos',teams->'registros');
  if p_simular then raise exception using errcode='ZX120',message='Reversion de acta y estadisticas'; end if;
 exception when sqlstate 'ZX120' then
  if not p_simular then raise; end if;
 end;
 return result;
end; $$;
revoke all on function public.importar_acta_estadisticas_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_acta_estadisticas_m14(jsonb,boolean) to service_role;
commit;

begin;
create or replace function public.probar_acta_estadisticas_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare src record; dst record; payload jsonb; k text; t text; rows jsonb; before_counts jsonb:='{}'; after_counts jsonb:='{}'; n bigint; snapshot jsonb; result jsonb; repeated jsonb; invalid_payload jsonb; mode boolean; caught boolean; error_message text; initial_snapshot jsonb:='{}'; final_snapshot jsonb:='{}'; saved jsonb; duplicate_result jsonb; first_snapshot jsonb:='{}'; second_snapshot jsonb:='{}'; digest text; stored_counts jsonb; rolled boolean:=false;
begin
 lock table competiciones,equipos,jornadas,partidos,actas,actas_versiones,jugadores,jugadores_equipos,alineaciones,anotaciones,sustituciones,sanciones,participaciones,personal_partidos,estadisticas_jugadores,estadisticas_equipos in share row exclusive mode;
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
 foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
  execute format('select count(*) from public.%I',t) into n;
  before_counts:=before_counts||jsonb_build_object(t,n);
  execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',t) into digest;
  initial_snapshot:=initial_snapshot||jsonb_build_object(t,digest);
 end loop;
 payload:=jsonb_set(p_acta,'{partido}',to_jsonb(dst.id));
 foreach k in array array['alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal'] loop
  select coalesce(jsonb_agg(case when v->>'equipo_id' is null then v else jsonb_set(v,'{equipo_id}',to_jsonb(case (v->>'equipo_id')::bigint when src.equipo_local_id then dst.equipo_local_id when src.equipo_visitante_id then dst.equipo_visitante_id end)) end order by ord),'[]'::jsonb)
  into rows from jsonb_array_elements(p_acta->k) with ordinality as e(v,ord);
  payload:=jsonb_set(payload,array[k],rows);
 end loop;
 result:=importar_acta_estadisticas_m14(payload,true);
 repeated:=importar_acta_estadisticas_m14(payload,true);
 if result is distinct from repeated or result->>'estado' is distinct from 'importado' or result->'simulacion' is distinct from 'true'::jsonb or result->'escritura' is distinct from 'false'::jsonb or result->'registros' is distinct from prevalidar_importacion_acta_m14(payload)->'registros' then raise exception 'Simulacion incompleta'; end if;

 -- 115: only the final staff record is changed; all earlier sections are real.
 if jsonb_array_length(payload->'personal')=0 then raise exception 'Sin personal para fallo final'; end if;
 invalid_payload:=jsonb_set(payload,array['personal',(jsonb_array_length(payload->'personal')-1)::text,'licencia'],'null'::jsonb);
 foreach mode in array array[true,false] loop
  caught:=false;
  begin
   perform importar_acta_estadisticas_m14(invalid_payload,mode);
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
 if importar_acta_estadisticas_m14(payload,true) is distinct from result then raise exception 'Recuperacion tras fallo discrepante'; end if;

 -- 116: exercise the saving path, then repeat against the stored acta.
 begin
  saved:=importar_acta_estadisticas_m14(payload,false);
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
  foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
   execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',t) into digest;
   first_snapshot:=first_snapshot||jsonb_build_object(t,digest);
  end loop;
  duplicate_result:=importar_acta_estadisticas_m14(payload,false);
  if duplicate_result->>'estado' is distinct from 'existente' or duplicate_result->'escritura' is distinct from 'false'::jsonb or duplicate_result->'simulacion' is distinct from 'false'::jsonb or duplicate_result->'estadisticas_jugadores' is distinct from saved->'estadisticas_jugadores' or duplicate_result->'estadisticas_equipos' is distinct from saved->'estadisticas_equipos' then raise exception 'Repeticion no reconocida'; end if;
  foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
   execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',t) into digest;
   second_snapshot:=second_snapshot||jsonb_build_object(t,digest);
  end loop;
  if first_snapshot is distinct from second_snapshot then raise exception 'Repeticion modifica registros'; end if;
  raise exception using errcode='ZX116',message='Reversion obligatoria del guardado de prueba';
 exception when sqlstate 'ZX116' then rolled:=true;
 end;
 if not rolled then raise exception 'Guardado de prueba no revertido'; end if;
 foreach t in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
  execute format('select count(*) from public.%I',t) into n;
  after_counts:=after_counts||jsonb_build_object(t,n);
  execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',t) into digest;
  final_snapshot:=final_snapshot||jsonb_build_object(t,digest);
 end loop;
 if initial_snapshot is distinct from final_snapshot or before_counts is distinct from after_counts or (select to_jsonb(q) from partidos q where q.id=dst.id) is distinct from snapshot
 or exists(select 1 from actas where partido_id=dst.id)
 or exists(select 1 from alineaciones where partido_id=dst.id)
 or exists(select 1 from anotaciones where partido_id=dst.id)
 or exists(select 1 from sustituciones where partido_id=dst.id)
 or exists(select 1 from sanciones where partido_id=dst.id)
 or exists(select 1 from personal_partidos where partido_id=dst.id)
 then raise exception 'Reversion no confirmada'; end if;
 return jsonb_build_object('registros',result->'registros','repeticion',true,'fallo_final_revertido',true,'modos_probados',2,'recuperacion',true,'guardado_completo',true,'repeticion_sin_cambios',true,'reversion',true,'tablas_verificadas',13,'estadisticas_conciliadas',true,'escritura',false);
end; $$;
revoke all on function public.probar_acta_estadisticas_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_acta_estadisticas_m14(jsonb) to service_role;
commit;
