begin;
create or replace function public.recalcular_equipos_m14(p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare total integer; different integer; updated integer:=0; added integer:=0; result jsonb;
begin
 if p_simular is null then raise exception 'Modo obligatorio'; end if;
 lock table competiciones,equipos,jornadas,partidos,actas,alineaciones,anotaciones,sanciones,estadisticas_equipos in share row exclusive mode;
 create temporary table far119_matches on commit drop as
 select p.* from partidos p join jornadas jo on jo.id=p.jornada_id join competiciones c on c.id=jo.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and exists(select 1 from actas a where a.partido_id=p.id);
 alter table pg_temp.far119_matches add primary key(id);
 if exists(select 1 from pg_temp.far119_matches where puntos_local is null or puntos_visitante is null or estado<>'finalizado')
 then raise exception 'Acta con marcador incompleto'; end if;
 if exists(select 1 from sanciones s join pg_temp.far119_matches m on m.id=s.partido_id where s.minuto is null or s.minuto not between 0 and 36 or s.tipo not in ('temporal','definitiva')
 or not exists(select 1 from alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.jugador_id=s.jugador_id))
 then raise exception 'Sancion invalida para balance numerico'; end if;
 create temporary table far119_sides on commit drop as
 select m.id partido_id,m.equipo_local_id equipo_id,m.equipo_visitante_id rival_id,m.puntos_local pf,m.puntos_visitante pc,true local from pg_temp.far119_matches m
 union all
 select m.id,m.equipo_visitante_id,m.equipo_local_id,m.puntos_visitante,m.puntos_local,false from pg_temp.far119_matches m;
 alter table pg_temp.far119_sides add primary key(partido_id,equipo_id);
 if exists(select 1 from pg_temp.far119_sides s where not exists(select 1 from alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.titular))
 then raise exception 'Equipo sin titulares'; end if;
 if exists(select 1 from pg_temp.far119_sides s where s.pf<>coalesce((select sum(a.puntos) from anotaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id),0))
 then raise exception 'Puntos no conciliados'; end if;
 create temporary table far119_stats on commit drop as
 with scoring as (
  select a.partido_id,a.equipo_id,count(*) filter(where tipo='ensayo')::int ensayos,
  count(*) filter(where tipo='transformacion')::int transformaciones,
  count(*) filter(where tipo='ensayo_castigo')::int castigo
  from anotaciones a join pg_temp.far119_matches m on m.id=a.partido_id group by a.partido_id,a.equipo_id
 ), cards as (
  select a.partido_id,a.equipo_id,count(*) filter(where tipo='temporal')::int amarillas,
  count(*) filter(where tipo='definitiva')::int rojas
  from sanciones a join pg_temp.far119_matches m on m.id=a.partido_id group by a.partido_id,a.equipo_id
 ), occupancy as (
  select s.partido_id,s.equipo_id,t.minute,
  (select count(*) from alineaciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.titular)
  -(select count(distinct a.jugador_id) from sanciones a where a.partido_id=s.partido_id and a.equipo_id=s.equipo_id and a.minuto<=t.minute
    and (a.tipo='definitiva' or t.minute<a.minuto+5)) active
  from pg_temp.far119_sides s cross join generate_series(0,35) t(minute)
 ), balance as (
  select a.partido_id,a.equipo_id,count(*) filter(where a.active>b.active)::int superior,
  count(*) filter(where a.active<b.active)::int inferior
  from occupancy a join occupancy b on b.partido_id=a.partido_id and b.minute=a.minute and b.equipo_id<>a.equipo_id
  group by a.partido_id,a.equipo_id
 ), totals as (
  select s.equipo_id,count(*)::int partidos_jugados,count(*) filter(where s.pf>s.pc)::int victorias,
  count(*) filter(where s.pf=s.pc)::int empates,count(*) filter(where s.pf<s.pc)::int derrotas,
  sum(s.pf)::int puntos_favor,sum(s.pc)::int puntos_contra,sum(coalesce(a.ensayos,0))::int ensayos_favor,
  sum(coalesce(b.ensayos,0))::int ensayos_contra,sum(coalesce(a.transformaciones,0))::int transformaciones,
  sum(coalesce(a.castigo,0))::int ensayos_castigo_favor,sum(coalesce(c.amarillas,0))::int amarillas,sum(coalesce(c.rojas,0))::int rojas,
  sum(n.superior)::int minutos_superioridad,sum(n.inferior)::int minutos_inferioridad,count(*)::int actas_comprobadas,
  coalesce(sum(s.pf) filter(where s.local),0) pf_local,coalesce(sum(s.pf) filter(where not s.local),0) pf_visitante,
  count(*) filter(where s.local) partidos_local,count(*) filter(where not s.local) partidos_visitante
  from pg_temp.far119_sides s left join scoring a on a.partido_id=s.partido_id and a.equipo_id=s.equipo_id
  left join scoring b on b.partido_id=s.partido_id and b.equipo_id=s.rival_id
  left join cards c on c.partido_id=s.partido_id and c.equipo_id=s.equipo_id
  join balance n on n.partido_id=s.partido_id and n.equipo_id=s.equipo_id group by s.equipo_id
 ), keys as (
  select equipo_id from totals
  union select s.equipo_id from estadisticas_equipos s join equipos e on e.id=s.equipo_id join competiciones c on c.id=e.competicion_id where c.id_oficial in ('35/11','35/12','35/15')
 )
 select k.equipo_id,coalesce(a.partidos_jugados,0)::int partidos_jugados,coalesce(a.victorias,0)::int victorias,coalesce(a.empates,0)::int empates,coalesce(a.derrotas,0)::int derrotas,coalesce(a.puntos_favor,0)::int puntos_favor,coalesce(a.puntos_contra,0)::int puntos_contra,coalesce(a.ensayos_favor,0)::int ensayos_favor,coalesce(a.ensayos_contra,0)::int ensayos_contra,coalesce(a.transformaciones,0)::int transformaciones,coalesce(a.ensayos_castigo_favor,0)::int ensayos_castigo_favor,coalesce(a.amarillas,0)::int amarillas,coalesce(a.rojas,0)::int rojas,coalesce(a.minutos_superioridad,0)::int minutos_superioridad,coalesce(a.minutos_inferioridad,0)::int minutos_inferioridad,coalesce(a.actas_comprobadas,0)::int actas_comprobadas,
 jsonb_build_object('puntos_por_partido',round(a.puntos_favor::numeric/nullif(a.partidos_jugados,0),4),
 'puntos_contra_por_partido',round(a.puntos_contra::numeric/nullif(a.partidos_jugados,0),4),
 'proporcion_ensayos_transformados',round(a.transformaciones::numeric/nullif(a.ensayos_favor,0),4),
 'victorias',round(a.victorias::numeric/nullif(a.partidos_jugados,0),4),
 'superioridad',round(a.minutos_superioridad::numeric/nullif(36*a.actas_comprobadas,0),4),
 'inferioridad',round(a.minutos_inferioridad::numeric/nullif(36*a.actas_comprobadas,0),4),
 'puntos_local_por_partido',round(a.pf_local::numeric/nullif(a.partidos_local,0),4),
 'puntos_visitante_por_partido',round(a.pf_visitante::numeric/nullif(a.partidos_visitante,0),4)) datos_adicionales
 from keys k left join totals a using(equipo_id);
 alter table pg_temp.far119_stats add primary key(equipo_id);
 if exists(select 1 from pg_temp.far119_stats where victorias+empates+derrotas<>partidos_jugados or minutos_superioridad+minutos_inferioridad>36*partidos_jugados)
 then raise exception 'Balance de equipo invalido'; end if;
 if exists(select 1 from estadisticas_equipos e join pg_temp.far119_stats s using(equipo_id) group by e.equipo_id having count(*)>1) then raise exception 'Estadisticas duplicadas'; end if;
 select count(*) into total from pg_temp.far119_stats;
 select count(*) into different from pg_temp.far119_stats s left join estadisticas_equipos e using(equipo_id)
 where (e.partidos_jugados,e.victorias,e.empates,e.derrotas,e.puntos_favor,e.puntos_contra,e.ensayos_favor,e.ensayos_contra,e.transformaciones,e.ensayos_castigo_favor,e.amarillas,e.rojas,e.minutos_superioridad,e.minutos_inferioridad,e.actas_comprobadas,e.datos_adicionales) is distinct from (s.partidos_jugados,s.victorias,s.empates,s.derrotas,s.puntos_favor,s.puntos_contra,s.ensayos_favor,s.ensayos_contra,s.transformaciones,s.ensayos_castigo_favor,s.amarillas,s.rojas,s.minutos_superioridad,s.minutos_inferioridad,s.actas_comprobadas,s.datos_adicionales);
 select jsonb_build_object('registros',total,'discrepancias',different,'actas',(select count(*) from pg_temp.far119_matches),
 'totales',jsonb_build_object('partidos_jugados',coalesce(sum(partidos_jugados),0),'victorias',coalesce(sum(victorias),0),'empates',coalesce(sum(empates),0),'derrotas',coalesce(sum(derrotas),0),'puntos_favor',coalesce(sum(puntos_favor),0),'puntos_contra',coalesce(sum(puntos_contra),0),'ensayos_favor',coalesce(sum(ensayos_favor),0),'ensayos_contra',coalesce(sum(ensayos_contra),0),'transformaciones',coalesce(sum(transformaciones),0),'ensayos_castigo_favor',coalesce(sum(ensayos_castigo_favor),0),'amarillas',coalesce(sum(amarillas),0),'rojas',coalesce(sum(rojas),0),'minutos_superioridad',coalesce(sum(minutos_superioridad),0),'minutos_inferioridad',coalesce(sum(minutos_inferioridad),0),'actas_comprobadas',coalesce(sum(actas_comprobadas),0))) into result from pg_temp.far119_stats;
 if not p_simular then
  update estadisticas_equipos e set partidos_jugados=s.partidos_jugados,victorias=s.victorias,empates=s.empates,derrotas=s.derrotas,puntos_favor=s.puntos_favor,puntos_contra=s.puntos_contra,ensayos_favor=s.ensayos_favor,ensayos_contra=s.ensayos_contra,transformaciones=s.transformaciones,ensayos_castigo_favor=s.ensayos_castigo_favor,amarillas=s.amarillas,rojas=s.rojas,minutos_superioridad=s.minutos_superioridad,minutos_inferioridad=s.minutos_inferioridad,actas_comprobadas=s.actas_comprobadas,datos_adicionales=s.datos_adicionales
  from pg_temp.far119_stats s where e.equipo_id=s.equipo_id
  and (e.partidos_jugados,e.victorias,e.empates,e.derrotas,e.puntos_favor,e.puntos_contra,e.ensayos_favor,e.ensayos_contra,e.transformaciones,e.ensayos_castigo_favor,e.amarillas,e.rojas,e.minutos_superioridad,e.minutos_inferioridad,e.actas_comprobadas,e.datos_adicionales) is distinct from (s.partidos_jugados,s.victorias,s.empates,s.derrotas,s.puntos_favor,s.puntos_contra,s.ensayos_favor,s.ensayos_contra,s.transformaciones,s.ensayos_castigo_favor,s.amarillas,s.rojas,s.minutos_superioridad,s.minutos_inferioridad,s.actas_comprobadas,s.datos_adicionales);
  get diagnostics updated=row_count;
  insert into estadisticas_equipos(equipo_id,partidos_jugados,victorias,empates,derrotas,puntos_favor,puntos_contra,ensayos_favor,ensayos_contra,transformaciones,ensayos_castigo_favor,amarillas,rojas,minutos_superioridad,minutos_inferioridad,actas_comprobadas,datos_adicionales)
  select s.* from pg_temp.far119_stats s where not exists(select 1 from estadisticas_equipos e where e.equipo_id=s.equipo_id);
  get diagnostics added=row_count;
 end if;
 drop table pg_temp.far119_stats,pg_temp.far119_sides,pg_temp.far119_matches;
 return result||jsonb_build_object('simulacion',p_simular,'actualizados',updated,'insertados',added);
end; $$;
revoke all on function public.recalcular_equipos_m14(boolean) from public,anon,authenticated;
grant execute on function public.recalcular_equipos_m14(boolean) to service_role;
commit;
