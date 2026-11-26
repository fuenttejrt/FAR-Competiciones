begin;
drop function if exists public.importar_alineaciones_m14(jsonb);
create or replace function public.importar_alineaciones_m14(p_filas jsonb, p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; nt integer; nm integer; ni integer;
begin
 if p_simular is null then raise exception 'Modo obligatorio'; end if;
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_alineacion_stage(partido_id bigint not null,equipo_id bigint not null,licencia text not null,dorsal integer not null,titular boolean not null,primary key(partido_id,equipo_id,licencia),unique(partido_id,equipo_id,dorsal)) on commit drop;
 insert into pg_temp.far_alineacion_stage select partido_id,equipo_id,licencia,dorsal,titular from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,licencia text,dorsal integer,titular boolean);
 if exists(select 1 from pg_temp.far_alineacion_stage s left join public.partidos p on p.id=s.partido_id left join public.jornadas jo on jo.id=p.jornada_id left join public.competiciones c on c.id=jo.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15') or (s.equipo_id is distinct from p.equipo_local_id and s.equipo_id is distinct from p.equipo_visitante_id)) then raise exception 'Partido o equipo fuera de ámbito'; end if;
 if exists(select 1 from pg_temp.far_alineacion_stage s where (select count(*) from public.jugadores j join public.jugadores_equipos je on je.jugador_id=j.id and je.equipo_id=s.equipo_id where j.licencia=s.licencia)<>1) then raise exception 'Identidad o relación no válida'; end if;
 if exists(select 1 from pg_temp.far_alineacion_stage where dorsal<=0) then raise exception 'Dorsal inválido'; end if;
 select count(*),count(*) filter(where titular),count(distinct partido_id) into n,nt,nm from pg_temp.far_alineacion_stage;
 if not p_simular then
   lock table public.alineaciones in share row exclusive mode;
   if exists(select 1 from public.alineaciones a join (select distinct partido_id from pg_temp.far_alineacion_stage) s on s.partido_id=a.partido_id group by a.partido_id,a.equipo_id,a.jugador_id having count(*)>1) then raise exception 'Alineaciones duplicadas existentes'; end if;
   if exists(select 1 from public.alineaciones a join (select distinct partido_id from pg_temp.far_alineacion_stage) p on p.partido_id=a.partido_id where not exists(select 1 from pg_temp.far_alineacion_stage s join public.jugadores j on j.licencia=s.licencia where s.partido_id=a.partido_id and s.equipo_id=a.equipo_id and j.id=a.jugador_id and s.dorsal is not distinct from a.dorsal and s.titular is not distinct from a.titular)) then raise exception 'Alineación existente discrepante'; end if;
   insert into public.alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular)
   select s.partido_id,s.equipo_id,j.id,s.dorsal,s.titular from pg_temp.far_alineacion_stage s join public.jugadores j on j.licencia=s.licencia
   where not exists(select 1 from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.jugador_id=j.id);
   get diagnostics ni = row_count;
   if (select count(*) from public.alineaciones a join pg_temp.far_alineacion_stage s on s.partido_id=a.partido_id and s.equipo_id=a.equipo_id join public.jugadores j on j.id=a.jugador_id and j.licencia=s.licencia where a.dorsal=s.dorsal and a.titular=s.titular)<>n then raise exception 'Alineaciones incompletas'; end if;
   drop table pg_temp.far_alineacion_stage;
   return jsonb_build_object('alineaciones',n,'titulares',nt,'suplentes',n-nt,'partidos',nm,'insertadas',ni,'simulacion',false);
 end if;
 drop table pg_temp.far_alineacion_stage;
 return jsonb_build_object('alineaciones',n,'titulares',nt,'suplentes',n-nt,'partidos',nm,'simulacion',true);
end; $$;
revoke all on function public.importar_alineaciones_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_alineaciones_m14(jsonb,boolean) to service_role;
commit;
