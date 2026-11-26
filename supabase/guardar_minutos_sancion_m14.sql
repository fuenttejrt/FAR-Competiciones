begin;
create or replace function public.guardar_minutos_sancion_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; changed integer:=0; total integer;
begin
 if p_simular is null or jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_sancion_min on commit drop as
 select x.* from jsonb_to_recordset(p_filas) as x(alineacion_id bigint,minutos_sancionado integer);
 alter table pg_temp.far_sancion_min add primary key(alineacion_id);
 select count(*),sum(minutos_sancionado) into n,total from pg_temp.far_sancion_min;
 if n<>2124 or total is distinct from 130 then raise exception 'Totales discrepantes'; end if;
 if exists(select 1 from pg_temp.far_sancion_min s left join public.participaciones p using(alineacion_id) where p.id is null or s.minutos_sancionado is null or s.minutos_sancionado<0 or s.minutos_sancionado+p.minutos_jugados>36) then raise exception 'Participación inválida'; end if;
 if not p_simular then
 lock table public.participaciones in share row exclusive mode;
 if exists(select 1 from pg_temp.far_sancion_min s join public.participaciones p using(alineacion_id) where p.minutos_sancionado is not null and p.minutos_sancionado<>s.minutos_sancionado) then raise exception 'Minutos existentes discrepantes'; end if;
 update public.participaciones p set minutos_sancionado=s.minutos_sancionado from pg_temp.far_sancion_min s where p.alineacion_id=s.alineacion_id and p.minutos_sancionado is null;
 get diagnostics changed=row_count;
 if exists(select 1 from pg_temp.far_sancion_min s left join public.participaciones p using(alineacion_id) where p.id is null or p.minutos_sancionado is distinct from s.minutos_sancionado) then raise exception 'Persistencia discrepante'; end if;
 end if;
 drop table pg_temp.far_sancion_min;
 return jsonb_build_object('participaciones',n,'minutos_sancionados',total,'actualizadas',changed,'simulacion',p_simular);
end; $$;
revoke all on function public.guardar_minutos_sancion_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.guardar_minutos_sancion_m14(jsonb,boolean) to service_role;
commit;
