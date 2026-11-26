begin;
create or replace function public.importar_estadisticas_jugadores_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; inserted integer:=0; resultado jsonb;
begin
 if p_simular is null or jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_stats_stage on commit drop as
 select j.id as jugador_id,x.equipo_id,x.convocatorias,x.titularidades,x.partidos_jugados,x.minutos_jugados,x.minutos_posibles,x.ensayos,x.transformaciones,x.puntos,x.amarillas,x.rojas,x.actas_participacion_verificada,x.datos_adicionales
 from jsonb_to_recordset(p_filas) as x(identity text,equipo_id bigint,convocatorias integer,titularidades integer,partidos_jugados integer,minutos_jugados integer,minutos_posibles integer,ensayos integer,transformaciones integer,puntos integer,amarillas integer,rojas integer,actas_participacion_verificada integer,datos_adicionales jsonb)
 left join public.jugadores j on j.licencia=x.identity;
 alter table pg_temp.far_stats_stage add primary key(jugador_id,equipo_id);
 if exists(select 1 from pg_temp.far_stats_stage s where not exists(select 1 from public.jugadores_equipos e where e.jugador_id=s.jugador_id and e.equipo_id=s.equipo_id) or s.convocatorias is null or s.titularidades is null or s.partidos_jugados is null or s.minutos_jugados is null or s.minutos_posibles is null or s.ensayos is null or s.transformaciones is null or s.puntos is null or s.amarillas is null or s.rojas is null or s.actas_participacion_verificada is null or least(s.convocatorias,s.titularidades,s.partidos_jugados,s.minutos_jugados,s.minutos_posibles,s.ensayos,s.transformaciones,s.puntos,s.amarillas,s.rojas)<0 or s.titularidades>s.convocatorias or s.partidos_jugados>s.convocatorias or s.minutos_jugados>s.minutos_posibles or s.minutos_posibles<>36*s.convocatorias or s.actas_participacion_verificada<>s.convocatorias) then raise exception 'Estadística inválida'; end if;
 select jsonb_build_object('registros',count(*),'identidades',count(distinct jugador_id),'equipos',count(distinct equipo_id),'convocatorias',sum(convocatorias),'titularidades',sum(titularidades),'partidos_jugados',sum(partidos_jugados),'minutos_jugados',sum(minutos_jugados),'minutos_posibles',sum(minutos_posibles),'ensayos',sum(ensayos),'transformaciones',sum(transformaciones),'puntos',sum(puntos),'amarillas',sum(amarillas),'rojas',sum(rojas)) into resultado from pg_temp.far_stats_stage;
 if resultado <> '{"registros":436,"identidades":432,"equipos":18,"convocatorias":2124,"titularidades":1584,"partidos_jugados":1840,"minutos_jugados":56894,"minutos_posibles":76464,"ensayos":295,"transformaciones":177,"puntos":1829,"amarillas":23,"rojas":4}'::jsonb then raise exception 'Totales discrepantes'; end if;
 if not p_simular then
 lock table public.estadisticas_jugadores in share row exclusive mode;
 if exists(select 1 from public.estadisticas_jugadores e join pg_temp.far_stats_stage s using(jugador_id,equipo_id) where (e.convocatorias,e.titularidades,e.partidos_jugados,e.minutos_jugados,e.minutos_posibles,e.ensayos,e.transformaciones,e.puntos,e.amarillas,e.rojas,e.actas_participacion_verificada,e.datos_adicionales) is distinct from (s.convocatorias,s.titularidades,s.partidos_jugados,s.minutos_jugados,s.minutos_posibles,s.ensayos,s.transformaciones,s.puntos,s.amarillas,s.rojas,s.actas_participacion_verificada,s.datos_adicionales)) then raise exception 'Estadísticas existentes discrepantes'; end if;
 insert into public.estadisticas_jugadores(jugador_id,equipo_id,convocatorias,titularidades,partidos_jugados,minutos_jugados,minutos_posibles,ensayos,transformaciones,puntos,amarillas,rojas,actas_participacion_verificada,datos_adicionales)
 select s.* from pg_temp.far_stats_stage s where not exists(select 1 from public.estadisticas_jugadores e where e.jugador_id=s.jugador_id and e.equipo_id=s.equipo_id);
 get diagnostics inserted=row_count;
 select count(*) into n from public.estadisticas_jugadores e join pg_temp.far_stats_stage s using(jugador_id,equipo_id);
 if n<>436 then raise exception 'Total almacenado incorrecto'; end if;
 end if;
 drop table pg_temp.far_stats_stage;
 return resultado||jsonb_build_object('simulacion',p_simular,'insertadas',inserted);
end; $$;
revoke all on function public.importar_estadisticas_jugadores_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_estadisticas_jugadores_m14(jsonb,boolean) to service_role;
commit;
