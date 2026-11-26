begin;
create or replace function public.validar_sustituciones_m14(p_filas jsonb)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; np integer;
begin
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_cambios_stage(partido_id bigint not null,equipo_id bigint not null,orden_evento integer not null,minuto integer not null,dorsal_entra integer not null,dorsal_sale integer not null,primary key(partido_id,orden_evento)) on commit drop;
 insert into pg_temp.far_cambios_stage select partido_id,equipo_id,orden_evento,minuto,dorsal_entra,dorsal_sale from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,orden_evento integer,minuto integer,dorsal_entra integer,dorsal_sale integer);
 if exists(select 1 from pg_temp.far_cambios_stage where minuto<0 or minuto>36 or orden_evento<1 or dorsal_entra<1 or dorsal_sale<1 or dorsal_entra=dorsal_sale) then raise exception 'Cambio inválido'; end if;
 if exists(select 1 from pg_temp.far_cambios_stage s left join public.partidos p on p.id=s.partido_id left join public.jornadas jo on jo.id=p.jornada_id left join public.competiciones c on c.id=jo.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15') or (s.equipo_id is distinct from p.equipo_local_id and s.equipo_id is distinct from p.equipo_visitante_id)) then raise exception 'Partido o equipo fuera de ámbito'; end if;
 if exists(select 1 from pg_temp.far_cambios_stage s where (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal_entra)<>1 or (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal_sale)<>1) then raise exception 'Cambio sin alineación única'; end if;
 if exists(select 1 from pg_temp.far_cambios_stage group by partido_id having min(orden_evento)<>1 or max(orden_evento)<>count(*)) then raise exception 'Orden de cambios incompleto'; end if;
 select count(*),count(distinct partido_id) into n,np from pg_temp.far_cambios_stage;
 drop table pg_temp.far_cambios_stage;
 return jsonb_build_object('sustituciones',n,'partidos_con_sustituciones',np,'simulacion',true);
end; $$;
revoke all on function public.validar_sustituciones_m14(jsonb) from public,anon,authenticated;
grant execute on function public.validar_sustituciones_m14(jsonb) to service_role;
commit;
