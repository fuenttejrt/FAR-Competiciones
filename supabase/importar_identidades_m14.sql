begin;
create or replace function public.importar_identidades_m14(p_filas jsonb, p_simular boolean default true)
returns jsonb language plpgsql security invoker set search_path=pg_catalog as $$
declare n integer; v_links integer; v_new integer; v_inserted integer; v_link_inserted integer;
begin
 if p_simular is null then raise exception 'Modo obligatorio'; end if;
 if jsonb_typeof(p_filas) is distinct from 'array' then raise exception 'Formato inválido'; end if;
 create temporary table far_identity_stage(licencia text not null, nombre text not null, equipo_id bigint not null, primary key(licencia,equipo_id)) on commit drop;
 insert into pg_temp.far_identity_stage select licencia,nombre,equipo_id from jsonb_to_recordset(p_filas) as x(licencia text,nombre text,equipo_id bigint);
 if exists(select 1 from pg_temp.far_identity_stage where licencia !~ '^[0-9]{7}$' or btrim(nombre)='') then raise exception 'Identidad inválida'; end if;
 if exists(select 1 from pg_temp.far_identity_stage group by licencia having count(distinct nombre)>1) then raise exception 'Nombre ambiguo'; end if;
 if exists(select 1 from pg_temp.far_identity_stage s left join public.equipos e on e.id=s.equipo_id left join public.competiciones c on c.id=e.competicion_id where c.id_oficial is null or c.id_oficial not in ('35/11','35/12','35/15')) then raise exception 'Equipo fuera de ámbito'; end if;
 select count(distinct licencia),count(*) into n,v_links from pg_temp.far_identity_stage;
 select count(distinct s.licencia) into v_new from pg_temp.far_identity_stage s where not exists(select 1 from public.jugadores j where j.licencia=s.licencia);
 if not p_simular then
   lock table public.jugadores, public.jugadores_equipos in share row exclusive mode;
   if exists(select 1 from public.jugadores where licencia is not null group by licencia having count(*)>1) then raise exception 'Licencias duplicadas existentes'; end if;
   if exists(select 1 from public.jugadores_equipos group by jugador_id,equipo_id having count(*)>1) then raise exception 'Relaciones duplicadas existentes'; end if;
   insert into public.jugadores(licencia,nombre_completo)
   select s.licencia,min(s.nombre) from pg_temp.far_identity_stage s
   where not exists(select 1 from public.jugadores j where j.licencia=s.licencia)
   group by s.licencia;
   get diagnostics v_inserted = row_count;
   insert into public.jugadores_equipos(jugador_id,equipo_id)
   select j.id,s.equipo_id from pg_temp.far_identity_stage s join public.jugadores j on j.licencia=s.licencia
   where not exists(select 1 from public.jugadores_equipos je where je.jugador_id=j.id and je.equipo_id=s.equipo_id);
   get diagnostics v_link_inserted = row_count;
   if (select count(*) from pg_temp.far_identity_stage s join public.jugadores j on j.licencia=s.licencia join public.jugadores_equipos je on je.jugador_id=j.id and je.equipo_id=s.equipo_id) <> v_links then raise exception 'Relaciones incompletas'; end if;
   drop table pg_temp.far_identity_stage;
   return jsonb_build_object('identidades',n,'relaciones',v_links,'insertados',v_inserted,'relaciones_insertadas',v_link_inserted,'simulacion',false);
 end if;
 drop table pg_temp.far_identity_stage;
 return jsonb_build_object('identidades',n,'relaciones',v_links,'nuevos',v_new,'simulacion',true);
end; $$;
revoke all on function public.importar_identidades_m14(jsonb,boolean) from public,anon,authenticated;
grant execute on function public.importar_identidades_m14(jsonb,boolean) to service_role;
commit;
