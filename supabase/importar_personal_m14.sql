create or replace function public.importar_personal_m14(p_filas jsonb,p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare n integer; inserted integer:=0;
begin
create temporary table tmp_personal_m14 on commit drop as
select * from jsonb_to_recordset(p_filas) as x(partido_id bigint,equipo_id bigint,funcion text,licencia text,nombre_completo text);
select count(*) into n from tmp_personal_m14;
if n<>472 or (select count(distinct partido_id) from tmp_personal_m14)<>53 or (select count(*) from tmp_personal_m14 where nombre_completo is null)<>3 then raise exception 'Cobertura incorrecta'; end if;
if exists(select 1 from tmp_personal_m14 where partido_id is null or licencia is null or btrim(licencia)='' or funcion is null or funcion not in ('Entrenador','Delegado','Juez línea','Árbitro principal','Delegado de campo','Médico') or (nombre_completo is null and funcion<>'Juez línea') or (nombre_completo is not null and btrim(nombre_completo)='') or ((equipo_id is null) <> (funcion in ('Árbitro principal','Delegado de campo','Médico')))) then raise exception 'Registro inválido'; end if;
if exists(select 1 from tmp_personal_m14 group by partido_id,equipo_id,funcion having count(*)>1) then raise exception 'Duplicados origen'; end if;
if exists(select 1 from tmp_personal_m14 t where not exists(select 1 from public.actas a where a.partido_id=t.partido_id) or (t.equipo_id is not null and not exists(select 1 from public.alineaciones a where a.partido_id=t.partido_id and a.equipo_id=t.equipo_id))) then raise exception 'Relación inválida'; end if;
lock table public.personal_partidos in share row exclusive mode;
if exists(select 1 from public.personal_partidos p join tmp_personal_m14 t on p.partido_id=t.partido_id and p.equipo_id is not distinct from t.equipo_id and p.funcion=t.funcion where p.licencia is distinct from t.licencia or p.nombre_completo is distinct from t.nombre_completo) then raise exception 'Conflicto existente'; end if;
if exists(select 1 from public.personal_partidos p join tmp_personal_m14 t on p.partido_id=t.partido_id and p.equipo_id is not distinct from t.equipo_id and p.funcion=t.funcion group by p.partido_id,p.equipo_id,p.funcion having count(*)>1) then raise exception 'Duplicados destino'; end if;
if not p_simular then
insert into public.personal_partidos(partido_id,equipo_id,funcion,licencia,nombre_completo)
select t.partido_id,t.equipo_id,t.funcion,t.licencia,t.nombre_completo from tmp_personal_m14 t where not exists(select 1 from public.personal_partidos p where p.partido_id=t.partido_id and p.equipo_id is not distinct from t.equipo_id and p.funcion=t.funcion);
get diagnostics inserted=row_count;
end if;
return jsonb_build_object('candidatos',n,'parciales',3,'insertados',inserted,'simulacion',p_simular);
end; $$;
revoke all on function public.importar_personal_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_personal_m14(jsonb,boolean) to service_role;
