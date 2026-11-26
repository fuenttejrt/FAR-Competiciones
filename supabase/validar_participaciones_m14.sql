begin;
drop function if exists public.validar_participaciones_m14(jsonb);
create or replace function public.validar_participaciones_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare resultado jsonb; restricciones jsonb; inserted integer:=0;
begin
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_participaciones_stage(partido_id bigint not null,equipo_id bigint not null,dorsal integer not null,titular boolean not null,minutos integer not null,verificada boolean not null,primary key(partido_id,equipo_id,dorsal)) on commit drop;
 insert into pg_temp.far_participaciones_stage select partido_id,equipo_id,dorsal,titular,minutos,verificada from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,dorsal integer,titular boolean,minutos integer,verificada boolean);
 if exists(select 1 from pg_temp.far_participaciones_stage where minutos not between 0 and 36 or dorsal<1 or not verificada) then raise exception 'Participación inválida'; end if;
 if exists(select 1 from pg_temp.far_participaciones_stage s left join public.partidos p on p.id=s.partido_id left join public.jornadas jo on jo.id=p.jornada_id left join public.competiciones c on c.id=jo.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15') or (s.equipo_id is distinct from p.equipo_local_id and s.equipo_id is distinct from p.equipo_visitante_id)) then raise exception 'Partido o equipo fuera de ámbito'; end if;
 if exists(select 1 from pg_temp.far_participaciones_stage s where (select count(*) from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal)<>1 or not exists(select 1 from public.alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal and a.jugador_id is not null and a.titular=s.titular)) then raise exception 'Alineación discrepante'; end if;
 if exists(select 1 from public.alineaciones a join (select distinct partido_id from pg_temp.far_participaciones_stage) p on p.partido_id=a.partido_id where not exists(select 1 from pg_temp.far_participaciones_stage s where s.partido_id=a.partido_id and s.equipo_id=a.equipo_id and s.dorsal=a.dorsal)) then raise exception 'Alineación sin participación'; end if;
 select jsonb_build_object('total',count(*),'partidos',count(distinct partido_id),'minutos',sum(minutos),'titulares',count(*) filter(where titular),'suplentes_con_minutos',count(*) filter(where not titular and minutos>0),'suplentes_sin_minutos',count(*) filter(where not titular and minutos=0),'jugadores_con_minutos',count(*) filter(where minutos>0),'simulacion',true) into resultado from pg_temp.far_participaciones_stage;
 if resultado<> '{"total":2124,"partidos":53,"minutos":56894,"titulares":1584,"suplentes_con_minutos":256,"suplentes_sin_minutos":284,"jugadores_con_minutos":1840,"simulacion":true}'::jsonb then raise exception 'Alcance inesperado'; end if;
 select coalesce(jsonb_agg(jsonb_build_object('nombre',conname,'definicion',pg_get_constraintdef(oid)) order by conname),'[]'::jsonb) into restricciones from pg_constraint where conrelid='public.participaciones'::regclass;
 if p_simular is null then raise exception 'Modo inválido'; end if;
 if not p_simular then
   lock table public.participaciones in share row exclusive mode;
   if exists(select 1 from pg_temp.far_participaciones_stage s join public.alineaciones a on a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal join public.participaciones p on p.alineacion_id=a.id where (p.minutos_jugados,p.jugado,p.estado_calculo) is distinct from (s.minutos,s.minutos>0,'reglamentario'::text)) then raise exception 'Participaciones existentes discrepantes'; end if;
   insert into public.participaciones(alineacion_id,minutos_jugados,jugado,estado_calculo,minutos_sancionado)
   select a.id,s.minutos,s.minutos>0,'reglamentario',null from pg_temp.far_participaciones_stage s join public.alineaciones a on a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.dorsal=s.dorsal where not exists(select 1 from public.participaciones p where p.alineacion_id=a.id);
   get diagnostics inserted=row_count;
   if (select count(*) from public.participaciones p join public.alineaciones a on a.id=p.alineacion_id join pg_temp.far_participaciones_stage s on s.partido_id=a.partido_id and s.equipo_id=a.equipo_id and s.dorsal=a.dorsal)<>2124 then raise exception 'Total almacenado incorrecto'; end if;
   resultado:=resultado||jsonb_build_object('simulacion',false,'insertadas',inserted);
 end if;
 drop table pg_temp.far_participaciones_stage;
 return resultado||jsonb_build_object('restricciones',restricciones);
end; $$;
revoke all on function public.validar_participaciones_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.validar_participaciones_m14(jsonb,boolean) to service_role;
commit;
