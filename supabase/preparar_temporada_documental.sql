begin;
-- 129: prepared entry point for future app integration. Not exposed to public clients.
create or replace function public.preparar_temporada_documental(p_temporada text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare start_year integer; end_year integer; season_id bigint; existing_id bigint; created boolean:=false; states jsonb;
begin
 if p_temporada is null or p_temporada !~ '^[0-9]{4}/[0-9]{2}$' then raise exception 'Formato de temporada invalido'; end if;
 start_year:=substring(p_temporada from 1 for 4)::integer;
 end_year:=substring(p_temporada from 6 for 2)::integer;
 if (start_year+1)%100<>end_year then raise exception 'Los anos deben ser consecutivos'; end if;
 if start_year<2020 or start_year>extract(year from current_date)::integer+2 then raise exception 'Temporada fuera del intervalo admitido'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('temporada_documental:'||p_temporada,129));
 select t.id into existing_id from public.temporadas t where t.nombre=p_temporada;
 if existing_id is null then
  -- Merely create the documentary season; never switch the active sporting season.
  insert into public.temporadas(nombre,activa) values(p_temporada,false)
  on conflict(nombre) do nothing returning id into season_id;
  created:=season_id is not null;
 end if;
 if season_id is null then
  select t.id into strict season_id from public.temporadas t where t.nombre=p_temporada;
 end if;
 insert into public.documentos_sincronizaciones(temporada_id,fuente)
 select season_id,f.codigo from public.documentos_fuentes f where f.habilitada
 on conflict(temporada_id,fuente) do nothing;
 select coalesce(jsonb_object_agg(s.fuente,s.estado),'{}'::jsonb) into states
 from public.documentos_sincronizaciones s where s.temporada_id=season_id;
 return jsonb_build_object('temporada_id',season_id,'temporada',p_temporada,'creada',created,'fuentes',states,
  'busqueda_programada',true,'reglas_copiadas',false,'competiciones_activadas',false);
end; $$;
revoke all on function public.preparar_temporada_documental(text) from public,anon,authenticated;
grant execute on function public.preparar_temporada_documental(text) to service_role;
commit;
