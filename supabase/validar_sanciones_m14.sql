begin;
drop function if exists public.validar_sanciones_m14(jsonb);
create or replace function public.validar_sanciones_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare resultado jsonb; restricciones jsonb; inserted integer:=0;
begin
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_sanciones_stage(partido_id bigint not null,equipo_id bigint not null,orden_evento integer not null,minuto integer not null,dorsal integer not null,tipo text not null,primary key(partido_id,orden_evento)) on commit drop;
 insert into pg_temp.far_sanciones_stage select partido_id,equipo_id,orden_evento,minuto,dorsal,tipo from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,orden_evento integer,minuto integer,dorsal integer,tipo text);
 if exists(select 1 from pg_temp.far_sanciones_stage where minuto not between 0 and 36 or orden_evento<1 or dorsal<1 or tipo not in ('Expulsiones temporales','Expulsiones definitivas')) then raise exception 'Sanción inválida'; end if;
 if exists(select 1 from pg_temp.far_sanciones_stage s left join public.partidos p on p.id=s.partido_id left join public.jornadas jo on jo.id=p.jornada_id left join public.competiciones c on c.id=jo.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15') or (s.equipo_id is distinct from p.equipo_local_id and s.equipo_id is distinct from p.equipo_visitante_id)) then raise exception 'Partido o equipo fuera de ámbito'; end if;
 if exists(select 1 from pg_temp.far_sanciones_stage s where (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal and a.jugador_id is not null)<>1 or (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal)<>1) then raise exception 'Sanción sin alineación única'; end if;
 if exists(select 1 from pg_temp.far_sanciones_stage group by partido_id having min(orden_evento)<>1 or max(orden_evento)<>count(*)) then raise exception 'Orden incompleto'; end if;
 select jsonb_build_object('total',count(*),'partidos',count(distinct partido_id),'amarillas',count(*) filter(where tipo='Expulsiones temporales'),'rojas',count(*) filter(where tipo='Expulsiones definitivas'),'simulacion',true) into resultado from pg_temp.far_sanciones_stage;
 if resultado<> '{"total":27,"partidos":16,"amarillas":23,"rojas":4,"simulacion":true}'::jsonb then raise exception 'Alcance inesperado'; end if;
 select coalesce(jsonb_agg(jsonb_build_object('nombre',conname,'definicion',pg_get_constraintdef(oid)) order by conname),'[]'::jsonb) into restricciones from pg_constraint where conrelid='public.sanciones'::regclass;
 if p_simular is null then raise exception 'Modo inválido'; end if;
 if not p_simular then
   lock table public.sanciones in share row exclusive mode;
   if exists(select 1 from public.sanciones a join (select distinct partido_id from pg_temp.far_sanciones_stage) p on p.partido_id=a.partido_id left join pg_temp.far_sanciones_stage s on s.partido_id=a.partido_id and s.orden_evento=a.orden_evento left join public.alineaciones al on al.partido_id=s.partido_id and al.equipo_id=s.equipo_id and al.dorsal=s.dorsal where s.partido_id is null or (a.equipo_id,a.minuto,a.dorsal,a.jugador_id,a.tipo,a.verificada) is distinct from (s.equipo_id,s.minuto,s.dorsal,al.jugador_id,case when s.tipo='Expulsiones temporales' then 'temporal' else 'definitiva' end,true)) then raise exception 'Sanciones existentes discrepantes'; end if;
   insert into public.sanciones(partido_id,equipo_id,orden_evento,minuto,dorsal,jugador_id,tipo,verificada)
   select s.partido_id,s.equipo_id,s.orden_evento,s.minuto,s.dorsal,al.jugador_id,case when s.tipo='Expulsiones temporales' then 'temporal' else 'definitiva' end,true from pg_temp.far_sanciones_stage s join public.alineaciones al on al.partido_id=s.partido_id and al.equipo_id=s.equipo_id and al.dorsal=s.dorsal where not exists(select 1 from public.sanciones a where a.partido_id=s.partido_id and a.orden_evento=s.orden_evento);
   get diagnostics inserted=row_count;
   if (select count(*) from public.sanciones a join (select distinct partido_id from pg_temp.far_sanciones_stage) p on p.partido_id=a.partido_id)<>27 then raise exception 'Total almacenado incorrecto'; end if;
   resultado:=resultado||jsonb_build_object('simulacion',false,'insertadas',inserted);
 end if;
 drop table pg_temp.far_sanciones_stage;
 return resultado||jsonb_build_object('restricciones',restricciones);
end; $$;
revoke all on function public.validar_sanciones_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.validar_sanciones_m14(jsonb,boolean) to service_role;
commit;
