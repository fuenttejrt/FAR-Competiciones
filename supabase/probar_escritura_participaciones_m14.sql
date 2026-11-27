begin;
create or replace function public.probar_escritura_participaciones_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare p record; j1 bigint; j2 bigint; a1 bigint; a2 bigint; bc bigint[]; rolled boolean:=false;
begin
 lock table jornadas,partidos,actas,alineaciones,anotaciones,sustituciones,sanciones,personal_partidos,participaciones in share row exclusive mode;
 if prevalidar_importacion_acta_m14(p_acta)->>'estado'<>'existente' then raise exception 'Origen no confirmado'; end if;
 select q.* into p from partidos q join jornadas j on j.id=q.jornada_id join competiciones c on c.id=j.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and q.estado='pendiente' and q.puntos_local is null and q.puntos_visitante is null and q.url_acta is null
 and not exists(select 1 from actas t where t.partido_id=q.id)
 and not exists(select 1 from alineaciones t where t.partido_id=q.id)
 and not exists(select 1 from anotaciones t where t.partido_id=q.id)
 and not exists(select 1 from sustituciones t where t.partido_id=q.id)
 and not exists(select 1 from sanciones t where t.partido_id=q.id)
 and not exists(select 1 from personal_partidos t where t.partido_id=q.id)
 order by q.id limit 1;
 if not found then raise exception 'Sin partido de prueba'; end if;
 select id into j1 from jugadores where licencia=(p_acta->'alineaciones'->0->>'licencia');
 select id into j2 from jugadores where licencia=(p_acta->'alineaciones'->1->>'licencia');
 if j1 is null or j2 is null or j1=j2 then raise exception 'Identidades invalidas'; end if;
 bc:=array[(select count(*) from alineaciones),(select count(*) from participaciones),(select count(*) from personal_partidos)];
 begin
 insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_local_id,j1,1,true) returning id into a1;
 insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_local_id,j2,2,false) returning id into a2;
 insert into participaciones(alineacion_id,jugado,minutos_jugados,minutos_sancionado,estado_calculo) values(a1,true,31,5,'reglamentario'),(a2,false,0,0,'reglamentario');
 insert into personal_partidos(partido_id,equipo_id,nombre_completo,licencia,funcion) values(p.id,null,'Arbitro ficticio','PRUEBA111','arbitro'),(p.id,p.equipo_local_id,'Entrenador ficticio','PRUEBA111-E','entrenador');
 if (select count(*) from participaciones where alineacion_id in(a1,a2))<>2 or (select sum(minutos_jugados) from participaciones where alineacion_id in(a1,a2))<>31 or (select sum(minutos_sancionado) from participaciones where alineacion_id in(a1,a2))<>5 or (select count(*) from personal_partidos where partido_id=p.id)<>2 then raise exception 'Datos no conciliados'; end if;
 begin
 insert into participaciones(alineacion_id,jugado,minutos_jugados,minutos_sancionado,estado_calculo) values(a1,true,31,5,'reglamentario');
 raise exception 'Duplicado permitido';
 exception when unique_violation then null;
 end;
 raise exception using errcode='ZX111',message='Reversion obligatoria';
 exception when sqlstate 'ZX111' then rolled:=true;
 end;
 if not rolled or bc<>array[(select count(*) from alineaciones),(select count(*) from participaciones),(select count(*) from personal_partidos)] or exists(select 1 from participaciones where alineacion_id in(a1,a2)) or exists(select 1 from alineaciones where partido_id=p.id) or exists(select 1 from personal_partidos where partido_id=p.id) then raise exception 'Reversion no confirmada'; end if;
 return jsonb_build_object('participaciones',2,'minutos_jugados',31,'minutos_sancionado',5,'personal',2,'duplicado_rechazado',true,'reversion',true);
end; $$;
revoke all on function public.probar_escritura_participaciones_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_escritura_participaciones_m14(jsonb) to service_role;
commit;
