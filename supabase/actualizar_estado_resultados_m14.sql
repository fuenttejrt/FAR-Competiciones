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
declare r record; pl integer; pv integer; estado_actual text; n integer:=0; iguales integer:=0; bloqueados integer:=0;
begin
 if p_simular is null or jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Entrada inválida'; end if;
 if jsonb_array_length(p_filas)>90 then raise exception 'Alcance excesivo'; end if;
 if (select count(*)<>count(distinct id) from jsonb_to_recordset(p_filas) as x(id bigint)) then raise exception 'IDs duplicados o nulos'; end if;
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.alineaciones,public.anotaciones,public.sustituciones,public.sanciones in share mode;
 for r in select * from jsonb_to_recordset(p_filas) as x(id bigint,jornada bigint,anterior jsonb,nuevo jsonb,tipo text) loop
  select p.puntos_local,p.puntos_visitante,p.estado::text into pl,pv,estado_actual from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where p.id=r.id and j.id=r.jornada and c.id_oficial in ('35/11','35/12','35/15');
  if not found then raise exception 'Partido fuera de alcance %',r.id; end if;
  if r.tipo is distinct from 'nuevo' or r.anterior is distinct from '[null,null]'::jsonb or jsonb_typeof(r.nuevo) is distinct from 'array' then bloqueados:=bloqueados+1; continue; end if;
  if jsonb_array_length(r.nuevo)<>2 or jsonb_typeof(r.nuevo->0) is distinct from 'number' or jsonb_typeof(r.nuevo->1) is distinct from 'number' or (r.nuevo->>0)!~'^[0-9]+$' or (r.nuevo->>1)!~'^[0-9]+$' then raise exception 'Marcador inválido'; end if;
  if jsonb_build_array(pl,pv)=r.nuevo and estado_actual='finalizado' then iguales:=iguales+1; continue; end if;
  if pl is not null or pv is not null then raise exception 'Marcador concurrente %',r.id; end if;
  if estado_actual is distinct from 'pendiente' then bloqueados:=bloqueados+1; continue; end if;
  if exists(select 1 from actas where partido_id=r.id) or exists(select 1 from alineaciones where partido_id=r.id) or exists(select 1 from anotaciones where partido_id=r.id) or exists(select 1 from sustituciones where partido_id=r.id) or exists(select 1 from sanciones where partido_id=r.id) then bloqueados:=bloqueados+1; continue; end if;
  n:=n+1;
  if not p_simular then
   update partidos set puntos_local=(r.nuevo->>0)::integer,puntos_visitante=(r.nuevo->>1)::integer,estado='finalizado' where id=r.id;
   insert into historial_resultados_m14(partido_id,anterior,nuevo) values(r.id,jsonb_build_object('marcador',r.anterior,'estado',estado_actual),jsonb_build_object('marcador',r.nuevo,'estado','finalizado'));
  end if;
 end loop;
 return jsonb_build_object('candidatos',n,'iguales',iguales,'bloqueados',bloqueados,'simulacion',p_simular);
end; $$;
revoke all on function public.actualizar_resultados_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.actualizar_resultados_m14(jsonb,boolean) to service_role;

create or replace function public.probar_resultados_m14()
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; jid bigint; payload jsonb; first_result jsonb; second_result jsonb; before_count bigint; after_count bigint; pl integer; pv integer; estado_prueba text; estados jsonb; passed boolean:=false;
begin
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.alineaciones,public.anotaciones in share mode;
 select p.id,p.jornada_id into pid,jid from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where c.id_oficial in ('35/11','35/12','35/15') and p.estado='pendiente' and p.puntos_local is null and p.puntos_visitante is null and not exists(select 1 from actas a where a.partido_id=p.id) and not exists(select 1 from alineaciones a where a.partido_id=p.id) and not exists(select 1 from anotaciones a where a.partido_id=p.id) order by p.id limit 1;
 if not found then raise exception 'Sin candidato de prueba'; end if;
 select jsonb_object_agg(coalesce(estado::text,'NULL'),n) into estados from (select p.estado,count(*) n from partidos p join jornadas j on j.id=p.jornada_id join competiciones c on c.id=j.competicion_id where c.id_oficial in ('35/11','35/12','35/15') group by p.estado) s;
 select count(*) into before_count from historial_resultados_m14;
 payload:=jsonb_build_array(jsonb_build_object('id',pid,'jornada',jid,'anterior',jsonb_build_array(null,null),'nuevo',jsonb_build_array(12,7),'tipo','nuevo'));
 begin
  first_result:=actualizar_resultados_m14(payload,false);
  if first_result->>'candidatos'<>'1' then raise exception 'Escritura no confirmada'; end if;
  select puntos_local,puntos_visitante,estado::text into pl,pv,estado_prueba from partidos where id=pid;
  if pl is distinct from 12 or pv is distinct from 7 or estado_prueba is distinct from 'finalizado' then raise exception 'Marcador no persistido'; end if;
  second_result:=actualizar_resultados_m14(payload,false);
  if second_result->>'iguales'<>'1' or second_result->>'candidatos'<>'0' then raise exception 'Repetición incorrecta'; end if;
  select count(*) into after_count from historial_resultados_m14;
  if after_count<>before_count+1 or not exists(select 1 from historial_resultados_m14 where partido_id=pid and anterior=jsonb_build_object('marcador','[null,null]'::jsonb,'estado','pendiente') and nuevo=jsonb_build_object('marcador','[12,7]'::jsonb,'estado','finalizado')) then raise exception 'Historial incorrecto'; end if;
  raise exception using errcode='ZX080',message='Reversión deliberada de prueba';
 exception when sqlstate 'ZX080' then passed:=true;
 end;
 select puntos_local,puntos_visitante,estado::text into pl,pv,estado_prueba from partidos where id=pid;
 select count(*) into after_count from historial_resultados_m14;
 if not passed or pl is not null or pv is not null or estado_prueba is distinct from 'pendiente' or after_count<>before_count then raise exception 'Reversión no confirmada'; end if;
 return jsonb_build_object('escritura',true,'repeticion',true,'historial',true,'reversion',true,'estado_verificado',true,'estados',estados);
end; $$;
revoke all on function public.probar_resultados_m14() from public,anon,authenticated;
grant execute on function public.probar_resultados_m14() to service_role;
commit;