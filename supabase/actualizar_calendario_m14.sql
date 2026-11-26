begin;
create table if not exists public.historial_calendario_m14 (
 id bigint generated always as identity primary key,
 entidad text not null check(entidad in ('partido','jornada')),
 entidad_id bigint not null,
 anterior jsonb not null,
 nuevo jsonb not null,
 registrado_en timestamptz not null default now()
);
alter table public.historial_calendario_m14 enable row level security;
revoke all on public.historial_calendario_m14 from public,anon,authenticated;
grant select,insert on public.historial_calendario_m14 to service_role;
create or replace function public.actualizar_calendario_m14(p_partidos jsonb,p_jornadas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare r record; actual timestamptz; fi date; ff date; np integer:=0; nj integer:=0;
begin
 if p_simular is null or jsonb_typeof(p_partidos) is distinct from 'array' or jsonb_typeof(p_jornadas) is distinct from 'array' then raise exception 'Entrada inválida'; end if;
 if jsonb_array_length(p_partidos)>90 or jsonb_array_length(p_jornadas)>30 then raise exception 'Alcance excesivo'; end if;
 lock table public.jornadas,public.partidos in share row exclusive mode;
 if (select count(*)<>count(distinct id) from jsonb_to_recordset(p_partidos) as x(id bigint)) or (select count(*)<>count(distinct id) from jsonb_to_recordset(p_jornadas) as x(id bigint)) then raise exception 'IDs duplicados o nulos'; end if;
 for r in select * from jsonb_to_recordset(p_partidos) as x(id bigint,jornada bigint,anterior timestamptz,nueva timestamptz) loop
  select p.fecha_hora into actual from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where p.id=r.id and j.id=r.jornada and c.id_oficial in ('35/11','35/12','35/15') and p.puntos_local is null and p.puntos_visitante is null;
  if not found or r.nueva is null or (r.nueva at time zone 'Europe/Madrid')::time=time '00:00' then raise exception 'Partido no elegible %',r.id; end if;
  if actual is not distinct from r.nueva then continue; end if;
  if actual is distinct from r.anterior then raise exception 'Fecha concurrente %',r.id; end if;
  np:=np+1;
  if not p_simular then
   update partidos set fecha_hora=r.nueva where id=r.id;
   insert into historial_calendario_m14(entidad,entidad_id,anterior,nuevo) values('partido',r.id,jsonb_build_object('fecha_hora',actual),jsonb_build_object('fecha_hora',r.nueva));
  end if;
 end loop;
 for r in select * from jsonb_to_recordset(p_jornadas) as x(id bigint,anterior date,nueva date) loop
  select j.fecha_inicio,j.fecha_fin into fi,ff from jornadas j join competiciones c on c.id=j.competicion_id where j.id=r.id and c.id_oficial in ('35/11','35/12','35/15');
  if not found or r.nueva is null or r.anterior is null then raise exception 'Jornada no elegible'; end if;
  if (select count(*) from partidos where jornada_id=r.id)<>3 or (select count(*) from jsonb_to_recordset(p_partidos) as x(id bigint,jornada bigint,nueva timestamptz) join partidos p on p.id=x.id and p.jornada_id=x.jornada where x.jornada=r.id and (x.nueva at time zone 'Europe/Madrid')::date=r.nueva)<>3 then raise exception 'Jornada sin tres partidos confirmados'; end if;
  if fi=r.nueva then continue; end if;
  if fi is distinct from r.anterior or (ff is not null and ff<>fi) then raise exception 'Fecha jornada concurrente o intervalo'; end if;
  nj:=nj+1;
  if not p_simular then
   update jornadas set fecha_inicio=r.nueva,fecha_fin=case when ff is null then null else r.nueva end where id=r.id;
   insert into historial_calendario_m14(entidad,entidad_id,anterior,nuevo) values('jornada',r.id,jsonb_build_object('fecha_inicio',fi,'fecha_fin',ff),jsonb_build_object('fecha_inicio',r.nueva,'fecha_fin',case when ff is null then null else r.nueva end));
  end if;
 end loop;
 return jsonb_build_object('partidos',np,'jornadas',nj,'simulacion',p_simular);
end; $$;
revoke all on function public.actualizar_calendario_m14(jsonb,jsonb,boolean) from public,anon,authenticated;
grant execute on function public.actualizar_calendario_m14(jsonb,jsonb,boolean) to service_role;
commit;