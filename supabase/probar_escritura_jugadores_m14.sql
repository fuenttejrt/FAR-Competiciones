begin;
create or replace function public.probar_escritura_jugadores_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare p record; v jsonb; jid bigint; nuevo bigint; equipo bigint; dorsal integer; lic text; nombre text; n integer:=0; before_counts bigint[]; after_counts bigint[]; rolled boolean:=false; fake text;
begin
 lock table public.jornadas,public.partidos,public.actas,public.alineaciones,public.anotaciones,public.sustituciones,public.sanciones,public.personal_partidos,public.jugadores,public.jugadores_equipos in share row exclusive mode;
 if prevalidar_importacion_acta_m14(p_acta)->>'estado'<>'existente' then raise exception 'Origen no confirmado'; end if;
 select q.* into p from partidos q join jornadas j on j.id=q.jornada_id join competiciones c on c.id=j.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and q.estado='pendiente' and q.puntos_local is null and q.puntos_visitante is null and q.url_acta is null
 and not exists(select 1 from actas a where a.partido_id=q.id)
 and not exists(select 1 from alineaciones a where a.partido_id=q.id)
 and not exists(select 1 from anotaciones a where a.partido_id=q.id)
 and not exists(select 1 from sustituciones a where a.partido_id=q.id)
 and not exists(select 1 from sanciones a where a.partido_id=q.id)
 and not exists(select 1 from personal_partidos a where a.partido_id=q.id)
 order by q.id limit 1;
 if not found then raise exception 'Sin partido de prueba'; end if;
 before_counts:=array[(select count(*) from jugadores),(select count(*) from jugadores_equipos),(select count(*) from alineaciones)];
 fake:='PRUEBA109-'||txid_current()::text;
 begin
 for v in select value from jsonb_array_elements(p_acta->'alineaciones') limit 2 loop
  lic:=v->>'licencia'; nombre:=v->>'nombre_completo';
  select id into jid from jugadores where licencia=lic and nombre_completo=nombre;
  if not found then raise exception 'Identidad no confirmada'; end if;
  equipo:=p.equipo_local_id; dorsal:=n+1;
  insert into jugadores_equipos(jugador_id,equipo_id) values(jid,equipo) on conflict(jugador_id,equipo_id) do nothing;
  insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,equipo,jid,dorsal,(v->>'titular')::boolean);
  n:=n+1;
 end loop;
 if n<>2 then raise exception 'Muestra insuficiente'; end if;
 insert into jugadores(licencia,nombre_completo) values(fake,'Jugador Ficticio Prueba') returning id into nuevo;
 insert into jugadores_equipos(jugador_id,equipo_id) values(nuevo,p.equipo_visitante_id);
 insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_visitante_id,nuevo,1,false);
 if (select count(*) from alineaciones a join jugadores_equipos je on je.jugador_id=a.jugador_id and je.equipo_id=a.equipo_id where a.partido_id=p.id)<>3 then raise exception 'Relaciones no conciliadas'; end if;
 begin
  insert into jugadores(licencia,nombre_completo) values(fake,'Duplicado Prueba');
  raise exception 'Duplicado permitido';
 exception when unique_violation then null;
 end;
 begin
  insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_visitante_id,nuevo,2,false);
  raise exception 'Alineación duplicada permitida';
 exception when unique_violation then null;
 end;
 raise exception using errcode='ZX109',message='Reversión obligatoria';
 exception when sqlstate 'ZX109' then rolled:=true;
 end;
 after_counts:=array[(select count(*) from jugadores),(select count(*) from jugadores_equipos),(select count(*) from alineaciones)];
 if not rolled or before_counts<>after_counts or exists(select 1 from jugadores where licencia=fake) or exists(select 1 from alineaciones where partido_id=p.id) then raise exception 'Reversión no confirmada'; end if;
 return jsonb_build_object('existentes',2,'nuevos',1,'alineaciones',3,'relaciones_verificadas',true,'duplicados_rechazados',true,'reversion',true);
end; $$;
revoke all on function public.probar_escritura_jugadores_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_escritura_jugadores_m14(jsonb) to service_role;
commit;
