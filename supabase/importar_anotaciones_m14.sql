begin;
create or replace function public.importar_anotaciones_m14(p_filas jsonb)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; np integer; ne integer; nt integer; nec integer; pts integer;
begin
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_anotacion_stage(partido_id bigint not null,equipo_id bigint not null,orden_evento integer not null,tipo text not null,minuto integer not null,dorsal integer,puntos integer not null,primary key(partido_id,equipo_id,orden_evento)) on commit drop;
 insert into pg_temp.far_anotacion_stage select partido_id,equipo_id,orden_evento,tipo,minuto,dorsal,puntos from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,orden_evento integer,tipo text,minuto integer,dorsal integer,puntos integer);
 if exists(select 1 from pg_temp.far_anotacion_stage where orden_evento<1 or minuto<0 or puntos is distinct from case tipo when 'E' then 5 when 'T' then 2 when 'D' then 3 when 'PC' then 3 when 'EC' then 7 end or (tipo='EC' and dorsal is not null) or (tipo<>'EC' and dorsal is null)) then raise exception 'Anotación inválida'; end if;
 if exists(select 1 from pg_temp.far_anotacion_stage s left join public.partidos p on p.id=s.partido_id left join public.jornadas jo on jo.id=p.jornada_id left join public.competiciones c on c.id=jo.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15') or (s.equipo_id is distinct from p.equipo_local_id and s.equipo_id is distinct from p.equipo_visitante_id)) then raise exception 'Partido o equipo fuera de ámbito'; end if;
 if exists(select 1 from pg_temp.far_anotacion_stage s where s.tipo<>'EC' and (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal)<>1) then raise exception 'Anotador sin alineación única'; end if;
 if exists(select 1 from pg_temp.far_anotacion_stage s join public.partidos p on p.id=s.partido_id group by s.partido_id,s.equipo_id,p.equipo_local_id,p.puntos_local,p.puntos_visitante having sum(s.puntos) is distinct from case when s.equipo_id=p.equipo_local_id then p.puntos_local else p.puntos_visitante end) then raise exception 'Marcador discrepante'; end if;
 select count(*),count(distinct partido_id),count(*) filter(where tipo='E'),count(*) filter(where tipo='T'),count(*) filter(where tipo='EC'),coalesce(sum(puntos),0) into n,np,ne,nt,nec,pts from pg_temp.far_anotacion_stage;
 drop table pg_temp.far_anotacion_stage;
 return jsonb_build_object('anotaciones',n,'partidos_con_anotaciones',np,'ensayos',ne,'transformaciones',nt,'ensayos_castigo',nec,'puntos',pts,'simulacion',true);
end; $$;
revoke all on function public.importar_anotaciones_m14(jsonb) from public,anon,authenticated;
grant execute on function public.importar_anotaciones_m14(jsonb) to service_role;
commit;
