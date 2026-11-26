begin;
create or replace function public.importar_estadisticas_equipos_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare resultado jsonb; inserted integer:=0; n integer;
begin
 if p_simular is null or jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_team_stage on commit drop as
 select x.* from jsonb_to_recordset(p_filas) as x(equipo_id bigint,partidos_jugados integer,victorias integer,empates integer,derrotas integer,puntos_favor integer,puntos_contra integer,ensayos_favor integer,ensayos_contra integer,transformaciones integer,ensayos_castigo_favor integer,amarillas integer,rojas integer,minutos_superioridad integer,minutos_inferioridad integer,actas_comprobadas integer,datos_adicionales jsonb);
 alter table pg_temp.far_team_stage add primary key(equipo_id);
 if exists(select 1 from pg_temp.far_team_stage s where not exists(select 1 from public.equipos e where e.id=s.equipo_id) or s.partidos_jugados is null or s.partidos_jugados<0 or s.victorias is null or s.victorias<0 or s.empates is null or s.empates<0 or s.derrotas is null or s.derrotas<0 or s.puntos_favor is null or s.puntos_favor<0 or s.puntos_contra is null or s.puntos_contra<0 or s.ensayos_favor is null or s.ensayos_favor<0 or s.ensayos_contra is null or s.ensayos_contra<0 or s.transformaciones is null or s.transformaciones<0 or s.ensayos_castigo_favor is null or s.ensayos_castigo_favor<0 or s.amarillas is null or s.amarillas<0 or s.rojas is null or s.rojas<0 or s.minutos_superioridad is null or s.minutos_superioridad<0 or s.minutos_inferioridad is null or s.minutos_inferioridad<0 or s.actas_comprobadas is null or s.actas_comprobadas<0 or s.victorias+s.empates+s.derrotas<>s.partidos_jugados or s.actas_comprobadas<>s.partidos_jugados or s.minutos_superioridad+s.minutos_inferioridad>36*s.partidos_jugados) then raise exception 'Estadística inválida'; end if;
 select jsonb_build_object('equipos',count(*),'partidos_jugados',sum(partidos_jugados),'victorias',sum(victorias),'empates',sum(empates),'derrotas',sum(derrotas),'puntos_favor',sum(puntos_favor),'puntos_contra',sum(puntos_contra),'ensayos_favor',sum(ensayos_favor),'ensayos_contra',sum(ensayos_contra),'transformaciones',sum(transformaciones),'ensayos_castigo_favor',sum(ensayos_castigo_favor),'amarillas',sum(amarillas),'rojas',sum(rojas),'minutos_superioridad',sum(minutos_superioridad),'minutos_inferioridad',sum(minutos_inferioridad),'actas_comprobadas',sum(actas_comprobadas)) into resultado from pg_temp.far_team_stage;
 if resultado <> '{"equipos":18,"partidos_jugados":106,"victorias":51,"empates":4,"derrotas":51,"puntos_favor":1850,"puntos_contra":1850,"ensayos_favor":295,"ensayos_contra":295,"transformaciones":177,"ensayos_castigo_favor":3,"amarillas":23,"rojas":4,"minutos_superioridad":315,"minutos_inferioridad":315,"actas_comprobadas":106}'::jsonb then raise exception 'Totales discrepantes'; end if;
 if not p_simular then
 lock table public.estadisticas_equipos in share row exclusive mode;
 if exists(select 1 from public.estadisticas_equipos e join pg_temp.far_team_stage s using(equipo_id) where (e.partidos_jugados,e.victorias,e.empates,e.derrotas,e.puntos_favor,e.puntos_contra,e.ensayos_favor,e.ensayos_contra,e.transformaciones,e.ensayos_castigo_favor,e.amarillas,e.rojas,e.minutos_superioridad,e.minutos_inferioridad,e.actas_comprobadas,e.datos_adicionales) is distinct from (s.partidos_jugados,s.victorias,s.empates,s.derrotas,s.puntos_favor,s.puntos_contra,s.ensayos_favor,s.ensayos_contra,s.transformaciones,s.ensayos_castigo_favor,s.amarillas,s.rojas,s.minutos_superioridad,s.minutos_inferioridad,s.actas_comprobadas,s.datos_adicionales)) then raise exception 'Estadísticas existentes discrepantes'; end if;
 insert into public.estadisticas_equipos(equipo_id,partidos_jugados,victorias,empates,derrotas,puntos_favor,puntos_contra,ensayos_favor,ensayos_contra,transformaciones,ensayos_castigo_favor,amarillas,rojas,minutos_superioridad,minutos_inferioridad,actas_comprobadas,datos_adicionales)
 select s.* from pg_temp.far_team_stage s where not exists(select 1 from public.estadisticas_equipos e where e.equipo_id=s.equipo_id);
 get diagnostics inserted=row_count;
 select count(*) into n from public.estadisticas_equipos e join pg_temp.far_team_stage s using(equipo_id);
 if n<>18 then raise exception 'Total almacenado incorrecto'; end if;
 end if;
 drop table pg_temp.far_team_stage;
 return resultado||jsonb_build_object('simulacion',p_simular,'insertadas',inserted);
end; $$;
revoke all on function public.importar_estadisticas_equipos_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_estadisticas_equipos_m14(jsonb,boolean) to service_role;
commit;
