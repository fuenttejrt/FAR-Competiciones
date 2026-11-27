begin;
create or replace function public.recalcular_jugadores_m14(p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare total integer; different integer; updated integer:=0; added integer:=0; result jsonb;
begin
 if p_simular is null then raise exception 'Modo obligatorio'; end if;
 lock table competiciones,equipos,jornadas,partidos,actas,jugadores,alineaciones,participaciones,anotaciones,sanciones,estadisticas_jugadores in share row exclusive mode;
 create temporary table far118_matches on commit drop as
 select p.id from partidos p join jornadas jo on jo.id=p.jornada_id join competiciones c on c.id=jo.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and exists(select 1 from actas a where a.partido_id=p.id);
 alter table pg_temp.far118_matches add primary key(id);
 if exists(select 1 from alineaciones a join pg_temp.far118_matches m on m.id=a.partido_id
  where (select count(*) from participaciones t where t.alineacion_id=a.id)<>1)
 then raise exception 'Participaciones incompletas'; end if;
 if exists(select 1 from anotaciones x join pg_temp.far118_matches m on m.id=x.partido_id
 where x.jugador_id is not null and not exists(select 1 from alineaciones a where a.partido_id=x.partido_id and a.equipo_id=x.equipo_id and a.jugador_id=x.jugador_id))
 or exists(select 1 from sanciones x join pg_temp.far118_matches m on m.id=x.partido_id
 where not exists(select 1 from alineaciones a where a.partido_id=x.partido_id and a.equipo_id=x.equipo_id and a.jugador_id=x.jugador_id))
 then raise exception 'Evento sin jugador alineado'; end if;
 create temporary table far118_stats on commit drop as
 with eligible as (
  select e.id from equipos e join competiciones c on c.id=e.competicion_id where c.id_oficial in ('35/11','35/12','35/15')
 ), roster as (
  select a.*,t.minutos_jugados from alineaciones a join pg_temp.far118_matches m on m.id=a.partido_id join participaciones t on t.alineacion_id=a.id
 ), activity as (
  select jugador_id,equipo_id,count(*)::int convocatorias,count(*) filter(where titular)::int titularidades,
  count(*) filter(where minutos_jugados>0)::int partidos_jugados,sum(minutos_jugados)::int minutos_jugados,
  (36*count(*))::int minutos_posibles,count(*)::int actas_participacion_verificada from roster group by jugador_id,equipo_id
 ), scores as (
  select a.jugador_id,a.equipo_id,count(*) filter(where tipo='ensayo')::int ensayos,
  count(*) filter(where tipo='transformacion')::int transformaciones,sum(puntos)::int puntos
  from anotaciones a join pg_temp.far118_matches m on m.id=a.partido_id
  where a.jugador_id is not null group by a.jugador_id,a.equipo_id
 ), cards as (
  select a.jugador_id,a.equipo_id,count(*) filter(where tipo='temporal')::int amarillas,
  count(*) filter(where tipo='definitiva')::int rojas
  from sanciones a join pg_temp.far118_matches m on m.id=a.partido_id group by a.jugador_id,a.equipo_id
 ), keys as (
  select jugador_id,equipo_id from activity
  union select s.jugador_id,s.equipo_id from estadisticas_jugadores s join eligible e on e.id=s.equipo_id
 )
 select k.jugador_id,k.equipo_id,coalesce(a.convocatorias,0)::int convocatorias,coalesce(a.titularidades,0)::int titularidades,coalesce(a.partidos_jugados,0)::int partidos_jugados,coalesce(a.minutos_jugados,0)::int minutos_jugados,coalesce(a.minutos_posibles,0)::int minutos_posibles,coalesce(s.ensayos,0)::int ensayos,coalesce(s.transformaciones,0)::int transformaciones,coalesce(s.puntos,0)::int puntos,coalesce(c.amarillas,0)::int amarillas,coalesce(c.rojas,0)::int rojas,coalesce(a.actas_participacion_verificada,0)::int actas_participacion_verificada,
 jsonb_build_object('participacion',round(a.minutos_jugados::numeric/nullif(a.minutos_posibles,0),4),
 'puntos_por_convocatoria',round(coalesce(s.puntos,0)::numeric/nullif(a.convocatorias,0),4),
 'titularidad',round(a.titularidades::numeric/nullif(a.convocatorias,0),4)) datos_adicionales
 from keys k left join activity a using(jugador_id,equipo_id) left join scores s using(jugador_id,equipo_id) left join cards c using(jugador_id,equipo_id);
 alter table pg_temp.far118_stats add primary key(jugador_id,equipo_id);
 if exists(select 1 from pg_temp.far118_stats where minutos_jugados<0 or minutos_jugados>minutos_posibles or titularidades>convocatorias or partidos_jugados>convocatorias) then raise exception 'Totales invalidos'; end if;
 if exists(select 1 from estadisticas_jugadores e join pg_temp.far118_stats s using(jugador_id,equipo_id) group by e.jugador_id,e.equipo_id having count(*)>1) then raise exception 'Estadisticas duplicadas'; end if;
 select count(*) into total from pg_temp.far118_stats;
 select count(*) into different from pg_temp.far118_stats s left join estadisticas_jugadores e using(jugador_id,equipo_id)
 where (e.convocatorias,e.titularidades,e.partidos_jugados,e.minutos_jugados,e.minutos_posibles,e.ensayos,e.transformaciones,e.puntos,e.amarillas,e.rojas,e.actas_participacion_verificada,e.datos_adicionales) is distinct from (s.convocatorias,s.titularidades,s.partidos_jugados,s.minutos_jugados,s.minutos_posibles,s.ensayos,s.transformaciones,s.puntos,s.amarillas,s.rojas,s.actas_participacion_verificada,s.datos_adicionales);
 select jsonb_build_object('registros',total,'discrepancias',different,'actas',(select count(*) from pg_temp.far118_matches),
 'totales',jsonb_build_object('convocatorias',coalesce(sum(convocatorias),0),'titularidades',coalesce(sum(titularidades),0),'partidos_jugados',coalesce(sum(partidos_jugados),0),'minutos_jugados',coalesce(sum(minutos_jugados),0),'minutos_posibles',coalesce(sum(minutos_posibles),0),'ensayos',coalesce(sum(ensayos),0),'transformaciones',coalesce(sum(transformaciones),0),'puntos',coalesce(sum(puntos),0),'amarillas',coalesce(sum(amarillas),0),'rojas',coalesce(sum(rojas),0),'actas_participacion_verificada',coalesce(sum(actas_participacion_verificada),0))) into result from pg_temp.far118_stats;
 if not p_simular then
  update estadisticas_jugadores e set convocatorias=s.convocatorias,titularidades=s.titularidades,partidos_jugados=s.partidos_jugados,minutos_jugados=s.minutos_jugados,minutos_posibles=s.minutos_posibles,ensayos=s.ensayos,transformaciones=s.transformaciones,puntos=s.puntos,amarillas=s.amarillas,rojas=s.rojas,actas_participacion_verificada=s.actas_participacion_verificada,datos_adicionales=s.datos_adicionales
  from pg_temp.far118_stats s where e.jugador_id=s.jugador_id and e.equipo_id=s.equipo_id
  and (e.convocatorias,e.titularidades,e.partidos_jugados,e.minutos_jugados,e.minutos_posibles,e.ensayos,e.transformaciones,e.puntos,e.amarillas,e.rojas,e.actas_participacion_verificada,e.datos_adicionales) is distinct from (s.convocatorias,s.titularidades,s.partidos_jugados,s.minutos_jugados,s.minutos_posibles,s.ensayos,s.transformaciones,s.puntos,s.amarillas,s.rojas,s.actas_participacion_verificada,s.datos_adicionales);
  get diagnostics updated=row_count;
  insert into estadisticas_jugadores(jugador_id,equipo_id,convocatorias,titularidades,partidos_jugados,minutos_jugados,minutos_posibles,ensayos,transformaciones,puntos,amarillas,rojas,actas_participacion_verificada,datos_adicionales)
  select s.* from pg_temp.far118_stats s where not exists(select 1 from estadisticas_jugadores e where e.jugador_id=s.jugador_id and e.equipo_id=s.equipo_id);
  get diagnostics added=row_count;
 end if;
 drop table pg_temp.far118_stats,pg_temp.far118_matches;
 return result||jsonb_build_object('simulacion',p_simular,'actualizados',updated,'insertados',added);
end; $$;
revoke all on function public.recalcular_jugadores_m14(boolean) from public,anon,authenticated;
grant execute on function public.recalcular_jugadores_m14(boolean) to service_role;
commit;
