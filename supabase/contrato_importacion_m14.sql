begin;
-- Inspect the actual database contract before implementing INSERT statements.
create or replace function public.contrato_importacion_m14()
returns jsonb language sql stable security invoker set search_path=public,pg_temp as $$
 select jsonb_build_object(
 'columnas',(select jsonb_agg(jsonb_build_object('tabla',table_name,'columna',column_name,'tipo',data_type,'nullable',is_nullable,'default',column_default,'identity',is_identity) order by table_name,ordinal_position) from information_schema.columns where table_schema='public' and table_name in ('actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','partidos')),
 'restricciones',(select jsonb_agg(jsonb_build_object('tabla',r.relname,'nombre',c.conname,'definicion',pg_get_constraintdef(c.oid)) order by r.relname,c.conname) from pg_constraint c join pg_class r on r.oid=c.conrelid join pg_namespace n on n.oid=r.relnamespace where n.nspname='public' and r.relname in ('actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','partidos')),
 'indices',(select jsonb_agg(jsonb_build_object('tabla',tablename,'definicion',indexdef) order by tablename,indexname) from pg_indexes where schemaname='public' and tablename in ('actas','actas_versiones','jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal_partidos','partidos'))
 );
$$;
revoke all on function public.contrato_importacion_m14() from public,anon,authenticated;
grant execute on function public.contrato_importacion_m14() to service_role;
commit;
