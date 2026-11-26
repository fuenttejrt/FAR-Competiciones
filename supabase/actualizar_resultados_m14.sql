begin;
create table if not exists public.historial_resultados_m14 (
 id bigint generated always as identity primary key,
 partido_id bigint not null references public.partidos(id),
 anterior jsonb not null,
 nuevo jsonb not null,
 registrado_en timestamptz not null default now()
);
alter table public.historial_resultados_m14 enable row level security;
revoke all on public.historial_resultados_m14 from public,anon,authenticated;
grant select,insert on public.historial_resultados_m14 to service_role;
create or replace function public.actualizar_resultados_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare r record; pl integer; pv integer; n integer:=0; iguales integer:=0; bloqueados integer:=0;
begin
 if p_simular is null or jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Entrada inválida'; end if;
 if jsonb_array_length(p_filas)>90 then raise exception 'Alcance excesivo'; end if;
 if (select count(*)<>count(distinct id) from jsonb_to_recordset(p_filas) as x(id bigint)) then raise exception 'IDs duplicados o nulos'; end if;
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.alineaciones,public.anotaciones in share mode;
 for r in select * from jsonb_to_recordset(p_filas) as x(id bigint,jornada bigint,anterior jsonb,nuevo jsonb,tipo text) loop
  select p.puntos_local,p.puntos_visitante into pl,pv from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where p.id=r.id and j.id=r.jornada and c.id_oficial in ('35/11','35/12','35/15');
  if not found then raise exception 'Partido fuera de alcance %',r.id; end if;
  if r.tipo is distinct from 'nuevo' or r.anterior is distinct from '[null,null]'::jsonb or jsonb_typeof(r.nuevo) is distinct from 'array' then bloqueados:=bloqueados+1; continue; end if;
  if jsonb_array_length(r.nuevo)<>2 or jsonb_typeof(r.nuevo->0) is distinct from 'number' or jsonb_typeof(r.nuevo->1) is distinct from 'number' or (r.nuevo->>0)!~'^[0-9]+$' or (r.nuevo->>1)!~'^[0-9]+$' then raise exception 'Marcador inválido'; end if;
  if jsonb_build_array(pl,pv)=r.nuevo then iguales:=iguales+1; continue; end if;
  if pl is not null or pv is not null then raise exception 'Marcador concurrente %',r.id; end if;
  if exists(select 1 from actas where partido_id=r.id) or exists(select 1 from alineaciones where partido_id=r.id) or exists(select 1 from anotaciones where partido_id=r.id) then bloqueados:=bloqueados+1; continue; end if;
  n:=n+1;
  if not p_simular then
   update partidos set puntos_local=(r.nuevo->>0)::integer,puntos_visitante=(r.nuevo->>1)::integer where id=r.id;
   insert into historial_resultados_m14(partido_id,anterior,nuevo) values(r.id,r.anterior,r.nuevo);
  end if;
 end loop;
 return jsonb_build_object('candidatos',n,'iguales',iguales,'bloqueados',bloqueados,'simulacion',p_simular);
end; $$;
revoke all on function public.actualizar_resultados_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.actualizar_resultados_m14(jsonb,boolean) to service_role;
commit;