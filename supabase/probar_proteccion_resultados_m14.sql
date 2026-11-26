begin;
create or replace function public.probar_proteccion_resultados_m14()
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; jid bigint; original jsonb; actual jsonb; payload jsonb; res jsonb; n bigint; passed boolean:=false; cobertura jsonb;
begin
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.alineaciones,public.anotaciones,public.sustituciones,public.sanciones in share mode;
 select p.id,p.jornada_id,to_jsonb(p) into pid,jid,original from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where c.id_oficial in ('35/11','35/12','35/15') and p.estado='finalizado' and exists(select 1 from actas a where a.partido_id=p.id) and exists(select 1 from alineaciones a where a.partido_id=p.id) and exists(select 1 from anotaciones a where a.partido_id=p.id) and exists(select 1 from sustituciones a where a.partido_id=p.id) and exists(select 1 from sanciones a where a.partido_id=p.id) order by p.id limit 1;
 if not found then raise exception 'Sin partido con cobertura completa'; end if;
 select count(*) into n from historial_resultados_m14;
 payload:=jsonb_build_array(jsonb_build_object('id',pid,'jornada',jid,'anterior','[null,null]'::jsonb,'nuevo','[12,7]'::jsonb,'tipo','nuevo'));
 begin
  update partidos set puntos_local=null,puntos_visitante=null,estado='pendiente' where id=pid;
  res:=actualizar_resultados_m14(payload,false);
  if res->>'bloqueados'<>'1' or res->>'candidatos'<>'0' then raise exception 'Protección fallida'; end if;
  if exists(select 1 from partidos where id=pid and (puntos_local is not null or puntos_visitante is not null or estado<>'pendiente')) then raise exception 'Marcador protegido modificado'; end if;
  raise exception using errcode='ZX082',message='Reversión deliberada';
 exception when sqlstate 'ZX082' then passed:=true;
 end;
 select to_jsonb(p) into actual from partidos p where id=pid;
 if not passed or actual is distinct from original or (select count(*) from historial_resultados_m14)<>n then raise exception 'Reversión fallida'; end if;
 return jsonb_build_object('bloqueado',true,'reversion',true,'historial_intacto',true,'cobertura',jsonb_build_array('actas','alineaciones','anotaciones','sustituciones','sanciones'));
end; $$;
revoke all on function public.probar_proteccion_resultados_m14() from public,anon,authenticated;
grant execute on function public.probar_proteccion_resultados_m14() to service_role;
commit;