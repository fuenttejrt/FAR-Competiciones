begin;
drop function if exists public.validar_sustituciones_m14(jsonb);
create or replace function public.validar_sustituciones_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; np integer; inserted integer:=0;
begin
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_cambios_stage(partido_id bigint not null,equipo_id bigint not null,orden_evento integer not null,minuto integer not null,dorsal_entra integer not null,dorsal_sale integer not null,primary key(partido_id,orden_evento)) on commit drop;
 insert into pg_temp.far_cambios_stage select partido_id,equipo_id,orden_evento,minuto,dorsal_entra,dorsal_sale from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,orden_evento integer,minuto integer,dorsal_entra integer,dorsal_sale integer);
 if exists(select 1 from pg_temp.far_cambios_stage where minuto<0 or minuto>36 or orden_evento<1 or dorsal_entra<1 or dorsal_sale<1 or dorsal_entra=dorsal_sale) then raise exception 'Cambio inválido'; end if;
 if exists(select 1 from pg_temp.far_cambios_stage s left join public.partidos p on p.id=s.partido_id left join public.jornadas jo on jo.id=p.jornada_id left join public.competiciones c on c.id=jo.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15') or (s.equipo_id is distinct from p.equipo_local_id and s.equipo_id is distinct from p.equipo_visitante_id)) then raise exception 'Partido o equipo fuera de ámbito'; end if;
 if exists(select 1 from pg_temp.far_cambios_stage s where (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal_entra)<>1 or (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal_sale)<>1) then raise exception 'Cambio sin alineación única'; end if;
 if exists(select 1 from pg_temp.far_cambios_stage group by partido_id having min(orden_evento)<>1 or max(orden_evento)<>count(*)) then raise exception 'Orden de cambios incompleto'; end if;
 select count(*),count(distinct partido_id) into n,np from pg_temp.far_cambios_stage;
 if p_simular is distinct from true then
   if p_simular is null or n<>271 or np<>39 then raise exception 'Alcance inesperado'; end if;
   lock table public.sustituciones in share row exclusive mode;
   if exists(select 1 from public.sustituciones a join (select distinct partido_id from pg_temp.far_cambios_stage) p on p.partido_id=a.partido_id left join pg_temp.far_cambios_stage s on s.partido_id=a.partido_id and s.orden_evento=a.orden_evento left join public.alineaciones en on en.partido_id=s.partido_id and en.equipo_id=s.equipo_id and en.dorsal=s.dorsal_entra left join public.alineaciones sa on sa.partido_id=s.partido_id and sa.equipo_id=s.equipo_id and sa.dorsal=s.dorsal_sale where s.partido_id is null or (a.equipo_id,a.minuto,a.dorsal_entra,a.dorsal_sale,a.jugador_entra_id,a.jugador_sale_id,a.verificada) is distinct from (s.equipo_id,s.minuto,s.dorsal_entra,s.dorsal_sale,en.jugador_id,sa.jugador_id,true)) then raise exception 'Sustituciones existentes discrepantes'; end if;
   insert into public.sustituciones(partido_id,equipo_id,orden_evento,minuto,dorsal_entra,dorsal_sale,jugador_entra_id,jugador_sale_id,verificada)
   select s.partido_id,s.equipo_id,s.orden_evento,s.minuto,s.dorsal_entra,s.dorsal_sale,en.jugador_id,sa.jugador_id,true from pg_temp.far_cambios_stage s join public.alineaciones en on en.partido_id=s.partido_id and en.equipo_id=s.equipo_id and en.dorsal=s.dorsal_entra join public.alineaciones sa on sa.partido_id=s.partido_id and sa.equipo_id=s.equipo_id and sa.dorsal=s.dorsal_sale where not exists(select 1 from public.sustituciones a where a.partido_id=s.partido_id and a.orden_evento=s.orden_evento);
   get diagnostics inserted = row_count;
   if (select count(*) from public.sustituciones a join (select distinct partido_id from pg_temp.far_cambios_stage) p on p.partido_id=a.partido_id)<>n then raise exception 'Total almacenado incorrecto'; end if;
 end if;
 drop table pg_temp.far_cambios_stage;
 return jsonb_build_object('sustituciones',n,'partidos_con_sustituciones',np,'simulacion',p_simular)||case when p_simular then '{}'::jsonb else jsonb_build_object('insertadas',inserted) end;
end; $$;
revoke all on function public.validar_sustituciones_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.validar_sustituciones_m14(jsonb,boolean) to service_role;
commit;
