-- Diagnostic only. No production rows are modified.
begin;
create or replace function public.probar_transaccion_m14()
returns jsonb
language plpgsql
security invoker
set search_path = pg_catalog
as $$
declare
  n integer;
  reverted boolean := false;
begin
  create temporary table far_m14_probe (id bigint primary key, puntos integer not null check (puntos >= 0)) on commit drop;
  insert into pg_temp.far_m14_probe values (1,5);
  insert into pg_temp.far_m14_probe values (1,5) on conflict (id) do update set puntos=excluded.puntos;
  select count(*) into n from pg_temp.far_m14_probe where puntos=5;
  if n <> 1 then raise exception 'Idempotencia incorrecta'; end if;
  begin
    insert into pg_temp.far_m14_probe values (2,7);
    insert into pg_temp.far_m14_probe values (3,-1);
  exception when check_violation then
    reverted := true;
  end;
  if not reverted or exists(select 1 from pg_temp.far_m14_probe where id in (2,3)) then
    raise exception 'Rollback incorrecto';
  end if;
  drop table pg_temp.far_m14_probe;
  return jsonb_build_object('idempotencia',true,'rollback',true,'filas_produccion',0);
end;
$$;
revoke all on function public.probar_transaccion_m14() from public, anon, authenticated;
grant execute on function public.probar_transaccion_m14() to service_role;
commit;
