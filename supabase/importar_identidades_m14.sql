begin;
create or replace function public.importar_identidades_m14(p_filas jsonb, p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; v_links integer; v_new integer;
begin
 if p_simular is distinct from true then raise exception 'Escritura todavía deshabilitada'; end if;
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_identity_stage(licencia text not null, nombre text not null, equipo_id bigint not null, primary key(licencia,equipo_id)) on commit drop;
 insert into pg_temp.far_identity_stage select licencia,nombre,equipo_id from jsonb_to_recordset(p_filas) as x(licencia text,nombre text,equipo_id bigint);
 if exists(select 1 from pg_temp.far_identity_stage where licencia !~ '^[0-9]{7}$' or btrim(nombre)='') then raise exception 'Identidad inválida'; end if;
 if exists(select 1 from pg_temp.far_identity_stage group by licencia having count(distinct nombre)>1) then raise exception 'Nombre ambiguo'; end if;
 if exists(select 1 from pg_temp.far_identity_stage s left join public.equipos e on e.id=s.equipo_id left join public.competiciones c on c.id=e.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15')) then raise exception 'Equipo fuera de ámbito'; end if;
 select count(distinct licencia),count(*) into n,v_links from pg_temp.far_identity_stage;
 select count(distinct s.licencia) into v_new from pg_temp.far_identity_stage s where not exists(select 1 from public.jugadores j where j.licencia=s.licencia);
 drop table pg_temp.far_identity_stage;
 return jsonb_build_object('identidades',n,'relaciones',v_links,'nuevos',v_new,'simulacion',true);
end; $$;
revoke all on function public.importar_identidades_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_identidades_m14(jsonb,boolean) to service_role;
commit;
