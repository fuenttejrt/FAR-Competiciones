begin;
create or replace function public.probar_proteccion_acta_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; before_row jsonb; before_acta jsonb; after_row jsonb; after_acta jsonb; rejected boolean:=false; rolled boolean:=false; baseline jsonb;
begin
 pid:=(p_acta->>'partido')::bigint;
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.alineaciones,public.anotaciones,public.sustituciones,public.sanciones,public.personal_partidos in share row exclusive mode;
 baseline:=prevalidar_importacion_acta_m14(p_acta);
 if baseline->>'estado'<>'existente' then raise exception 'Prueba requiere acta existente'; end if;
 select to_jsonb(p) into before_row from partidos p where id=pid;
 select to_jsonb(a) into before_acta from actas a where partido_id=pid;
 if not exists(select 1 from alineaciones where partido_id=pid) then raise exception 'Sin alineaciones de prueba'; end if;
 -- Simulate partial import without deleting an actual acta: use a temporary
 -- transaction-local test fixture by removing its association only.
 -- Existing acta remains in its table, so this test validates mismatch protection.
 begin
  update partidos set url_acta=null where id=pid;
  begin
   perform prevalidar_importacion_acta_m14(p_acta);
  exception when raise_exception then
   if sqlerrm<>'Acta existente discrepante' then raise; end if;
   rejected:=true;
  end;
  if not rejected then raise exception 'Asociación parcial aceptada'; end if;
  raise exception using errcode='ZX106',message='Reversión deliberada';
 exception when sqlstate 'ZX106' then rolled:=true;
 end;
 select to_jsonb(p) into after_row from partidos p where id=pid;
 select to_jsonb(a) into after_acta from actas a where partido_id=pid;
 if not rolled or before_row is distinct from after_row or before_acta is distinct from after_acta or prevalidar_importacion_acta_m14(p_acta) is distinct from baseline then raise exception 'Restauración incorrecta'; end if;
 return jsonb_build_object('rechazo',rejected,'reversion',rolled,'partido_intacto',true,'acta_intacta',true);
end; $$;
revoke all on function public.probar_proteccion_acta_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_proteccion_acta_m14(jsonb) to service_role;
commit;
