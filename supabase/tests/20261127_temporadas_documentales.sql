-- Run after instalar preparar_temporada_documental.sql. Always rolls back.
begin;
do $$
declare name text; first_result jsonb; repeated jsonb; initial_categories bigint; initial_competitions bigint; initial_active jsonb;
begin
 name:=(extract(year from current_date)::integer+1)::text||'/'||lpad(((extract(year from current_date)::integer+2)%100)::text,2,'0');
 select count(*) into initial_categories from public.categorias;
 select count(*) into initial_competitions from public.competiciones;
 select coalesce(jsonb_agg(t.id order by t.id),'[]'::jsonb) into initial_active from public.temporadas t where t.activa;
 first_result:=public.preparar_temporada_documental(name);
 repeated:=public.preparar_temporada_documental(name);
 if first_result->'temporada_id' is distinct from repeated->'temporada_id' or repeated->'creada' is distinct from 'false'::jsonb then raise exception 'Temporada duplicada'; end if;
 if (select count(*) from public.temporadas where nombre=name)<>1 then raise exception 'Registro de temporada incorrecto'; end if;
 if first_result->'reglas_copiadas' is distinct from 'false'::jsonb or first_result->'competiciones_activadas' is distinct from 'false'::jsonb then raise exception 'Activacion inesperada'; end if;
 if initial_categories<>(select count(*) from public.categorias) or initial_competitions<>(select count(*) from public.competiciones)
 or initial_active is distinct from (select coalesce(jsonb_agg(t.id order by t.id),'[]'::jsonb) from public.temporadas t where t.activa)
 then raise exception 'Configuracion deportiva modificada'; end if;
 begin
  perform public.preparar_temporada_documental('2026/30');
  raise exception using errcode='ZX129',message='Temporada invalida aceptada';
 exception when sqlstate 'P0001' then
  if sqlerrm<>'Los anos deben ser consecutivos' then raise; end if;
 end;
 raise notice 'Temporada unica, sin cambios deportivos y validacion comprobada';
end; $$;
rollback;
