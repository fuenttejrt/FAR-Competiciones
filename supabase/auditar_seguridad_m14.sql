create or replace function public.auditar_seguridad_m14()
returns jsonb language sql security invoker set search_path=pg_catalog,public as $$
select jsonb_build_object('tablas',coalesce(jsonb_agg(jsonb_build_object('tabla',c.relname,'rls',c.relrowsecurity,'rls_forzado',c.relforcerowsecurity,'anon_select',has_table_privilege('anon',c.oid,'SELECT'),'authenticated_select',has_table_privilege('authenticated',c.oid,'SELECT'),'anon_write',has_table_privilege('anon',c.oid,'INSERT,UPDATE,DELETE'),'authenticated_write',has_table_privilege('authenticated',c.oid,'INSERT,UPDATE,DELETE'),'politicas',(select coalesce(jsonb_agg(jsonb_build_object('nombre',p.policyname,'roles',p.roles,'comando',p.cmd,'using',p.qual,'check',p.with_check)),'[]'::jsonb) from pg_policies p where p.schemaname='public' and p.tablename=c.relname)) order by c.relname),'[]'::jsonb))
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relkind in ('r','p') and c.relname in ('jugadores','jugadores_equipos','alineaciones','anotaciones','sustituciones','sanciones','participaciones','estadisticas_jugadores','estadisticas_equipos','personal_partidos','actas','actas_versiones','validaciones');
$$;
revoke all on function public.auditar_seguridad_m14() from public,anon,authenticated;
grant execute on function public.auditar_seguridad_m14() to service_role;
