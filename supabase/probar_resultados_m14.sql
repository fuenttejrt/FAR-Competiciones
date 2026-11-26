begin;
create or replace function public.probar_resultados_m14()
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; jid bigint; payload jsonb; first_result jsonb; second_result jsonb; before_count bigint; after_count bigint; pl integer; pv integer; estados jsonb; passed boolean:=false;
begin
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.alineaciones,public.anotaciones in share mode;
 select p.id,p.jornada_id into pid,jid from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where c.id_oficial in ('35/11','35/12','35/15') and p.puntos_local is null and p.puntos_visitante is null and not exists(select 1 from actas a where a.partido_id=p.id) and not exists(select 1 from alineaciones a where a.partido_id=p.id) and not exists(select 1 from anotaciones a where a.partido_id=p.id) order by p.id limit 1;
 if not found then raise exception 'Sin candidato de prueba'; end if;
 select jsonb_object_agg(coalesce(estado::text,'NULL'),n) into estados from (select p.estado,count(*) n from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where c.id_oficial in ('35/11','35/12','35/15') group by p.estado) s;
 select count(*) into before_count from historial_resultados_m14;
 payload:=jsonb_build_array(jsonb_build_object('id',pid,'jornada',jid,'anterior',jsonb_build_array(null,null),'nuevo',jsonb_build_array(12,7),'tipo','nuevo'));
 begin
  first_result:=actualizar_resultados_m14(payload,false);
  if first_result->>'candidatos'<>'1' then raise exception 'Escritura no confirmada'; end if;
  select puntos_local,puntos_visitante into pl,pv from partidos where id=pid;
  if pl is distinct from 12 or pv is distinct from 7 then raise exception 'Marcador no persistido'; end if;
  second_result:=actualizar_resultados_m14(payload,false);
  if second_result->>'iguales'<>'1' or second_result->>'candidatos'<>'0' then raise exception 'Repetición incorrecta'; end if;
  select count(*) into after_count from historial_resultados_m14;
  if after_count<>before_count+1 or not exists(select 1 from historial_resultados_m14 where partido_id=pid and anterior='[null,null]'::jsonb and nuevo='[12,7]'::jsonb) then raise exception 'Historial incorrecto'; end if;
  raise exception using errcode='ZX080',message='Reversión deliberada de prueba';
 exception when sqlstate 'ZX080' then passed:=true;
 end;
 select puntos_local,puntos_visitante into pl,pv from partidos where id=pid;
 select count(*) into after_count from historial_resultados_m14;
 if not passed or pl is not null or pv is not null or after_count<>before_count then raise exception 'Reversión no confirmada'; end if;
 return jsonb_build_object('escritura',true,'repeticion',true,'historial',true,'reversion',true,'estados',estados);
end; $$;
revoke all on function public.probar_resultados_m14() from public,anon,authenticated;
grant execute on function public.probar_resultados_m14() to service_role;
commit;