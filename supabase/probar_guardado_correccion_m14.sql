begin;
create or replace function public.probar_guardado_correccion_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare current_act public.actas%rowtype; changed jsonb; row_part jsonb; part_index integer; target_player bigint; target_team bigint; original_minutes integer; next_hash text; first_result jsonb; second_result jsonb; rolled boolean:=false; tbl text; fingerprint text;
 before_all jsonb:='{}'; after_all jsonb:='{}'; after_save jsonb:='{}'; after_repeat jsonb:='{}';
begin
 lock table competiciones,equipos,jornadas,partidos,actas,actas_versiones,jugadores,jugadores_equipos,alineaciones,anotaciones,sustituciones,sanciones,participaciones,personal_partidos,estadisticas_jugadores,estadisticas_equipos in share row exclusive mode;
 if prevalidar_importacion_acta_m14(p_acta)->>'estado' is distinct from 'existente' then raise exception 'Origen no confirmado'; end if;
 if recalcular_jugadores_m14(true)->'discrepancias' is distinct from '0'::jsonb or recalcular_equipos_m14(true)->'discrepancias' is distinct from '0'::jsonb then raise exception 'Estadisticas iniciales discrepantes'; end if;
 select * into strict current_act from actas where partido_id=(p_acta->>'partido')::bigint;
 select e.value,(e.ord-1)::integer into row_part,part_index from jsonb_array_elements(p_acta->'participaciones') with ordinality e(value,ord)
 where (e.value->>'minutos_jugados')::int>1 order by e.ord limit 1;
 if not found then raise exception 'Sin participacion adecuada'; end if;
 target_team:=(row_part->>'equipo_id')::bigint;
 select a.jugador_id into strict target_player from alineaciones a where a.partido_id=current_act.partido_id and a.equipo_id=target_team and a.dorsal=(row_part->>'dorsal')::int;
 select e.minutos_jugados into strict original_minutes from estadisticas_jugadores e where e.jugador_id=target_player and e.equipo_id=target_team;
 next_hash:=md5('prueba123:'||current_act.hash_contenido)||md5('reversible123:'||current_act.hash_contenido);
 changed:=jsonb_set(p_acta,'{hash_contenido}',to_jsonb(next_hash));
 changed:=jsonb_set(changed,array['participaciones',part_index::text,'minutos_jugados'],to_jsonb((row_part->>'minutos_jugados')::int-1));
 foreach tbl in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
  execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',tbl) into fingerprint;
  before_all:=before_all||jsonb_build_object(tbl,fingerprint);
 end loop;
 begin
  first_result:=corregir_acta_estadisticas_m14(changed,current_act.version,current_act.hash_contenido,false);
  if first_result->>'estado' is distinct from 'corregido' or first_result->'escritura' is distinct from 'true'::jsonb or (first_result->>'version')::int is distinct from current_act.version+1 then raise exception 'Guardado de correccion incompleto'; end if;
  if not exists(select 1 from actas a where a.id=current_act.id and a.version=current_act.version+1 and a.hash_contenido=next_hash and a.datos=changed)
  or not exists(select 1 from actas_versiones v where v.acta_id=current_act.id and v.version=current_act.version and v.hash_contenido=current_act.hash_contenido and v.datos->'snapshot_previo'->'acta'=to_jsonb(current_act))
  or not exists(select 1 from actas_versiones v where v.acta_id=current_act.id and v.version=current_act.version+1 and v.hash_contenido=next_hash and v.datos=changed)
  then raise exception 'Historial de correccion no conservado'; end if;
  if (select e.minutos_jugados from estadisticas_jugadores e where e.jugador_id=target_player and e.equipo_id=target_team) is distinct from original_minutes-1 then raise exception 'Estadistica de jugador no actualizada'; end if;
  if recalcular_jugadores_m14(true)->'discrepancias' is distinct from '0'::jsonb or recalcular_equipos_m14(true)->'discrepancias' is distinct from '0'::jsonb then raise exception 'Estadisticas guardadas discrepantes'; end if;
 foreach tbl in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
  execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',tbl) into fingerprint;
  after_save:=after_save||jsonb_build_object(tbl,fingerprint);
 end loop;
  second_result:=corregir_acta_estadisticas_m14(changed,current_act.version,current_act.hash_contenido,false);
  if second_result is distinct from jsonb_build_object('estado','existente','version',current_act.version+1,'simulacion',false,'escritura',false) then raise exception 'Repeticion de correccion discrepante'; end if;
 foreach tbl in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
  execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',tbl) into fingerprint;
  after_repeat:=after_repeat||jsonb_build_object(tbl,fingerprint);
 end loop;
  if after_save is distinct from after_repeat then raise exception 'Repeticion de correccion modifica registros'; end if;
  raise exception using errcode='ZX123',message='Reversion obligatoria de correccion guardada';
 exception when sqlstate 'ZX123' then rolled:=true;
 end;
 foreach tbl in array array['partidos','actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','estadisticas_jugadores','estadisticas_equipos'] loop
  execute format('select md5(coalesce(string_agg(to_jsonb(q)::text, chr(10) order by to_jsonb(q)::text), '''')) from public.%I q',tbl) into fingerprint;
  after_all:=after_all||jsonb_build_object(tbl,fingerprint);
 end loop;
 if not rolled or before_all is distinct from after_all then raise exception 'Reversion de correccion guardada no confirmada'; end if;
 return jsonb_build_object('guardado_completo',true,'version_anterior_conservada',true,'estadistica_actualizada',true,'repeticion_sin_cambios',true,'tablas_verificadas',13,'reversion',true,'escritura',false);
end; $$;
revoke all on function public.probar_guardado_correccion_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_guardado_correccion_m14(jsonb) to service_role;
commit;
