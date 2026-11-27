begin;
create or replace function public.probar_escritura_acta_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; aid bigint; p record; original jsonb; result jsonb; rolled boolean:=false; payload jsonb; total_before bigint; version_before bigint;
begin
 lock table public.jornadas,public.partidos in share row exclusive mode;
 lock table public.actas,public.actas_versiones,public.alineaciones,public.anotaciones,public.sustituciones,public.sanciones,public.personal_partidos in share row exclusive mode;
 select p0.* into p from partidos p0 join jornadas j on j.id=p0.jornada_id join competiciones c on c.id=j.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and p0.estado='pendiente' and p0.puntos_local is null and p0.puntos_visitante is null and p0.url_acta is null
 and not exists(select 1 from actas a where a.partido_id=p0.id)
 and not exists(select 1 from alineaciones a where a.partido_id=p0.id)
 and not exists(select 1 from anotaciones a where a.partido_id=p0.id)
 and not exists(select 1 from sustituciones a where a.partido_id=p0.id)
 and not exists(select 1 from sanciones a where a.partido_id=p0.id)
 and not exists(select 1 from personal_partidos a where a.partido_id=p0.id)
 order by p0.id limit 1;
 if not found then raise exception 'Sin partido de prueba disponible'; end if;
 pid:=p.id; original:=to_jsonb(p);
 if prevalidar_importacion_acta_m14(p_acta)->>'estado'<>'existente' then raise exception 'Origen de prueba no confirmado'; end if;
 payload:=jsonb_build_object('prueba',true,'marcador',p_acta->'marcador');
 select count(*) into total_before from actas;
 select count(*) into version_before from actas_versiones;
 begin
  insert into actas(partido_id,url_pdf,hash_contenido,version,datos,estado_validacion,descargada_en,comprobada_en,version_procesador)
  values(pid,p_acta->>'url',p_acta->>'hash_contenido',1,payload,'pendiente',now(),now(),'108-prueba') returning id into aid;
  insert into actas_versiones(acta_id,version,hash_contenido,datos,motivo)
  values(aid,1,p_acta->>'hash_contenido',payload,'prueba reversible');
  update partidos set url_acta=p_acta->>'url' where id=pid;
  if not exists(select 1 from actas a join actas_versiones v on v.acta_id=a.id join partidos q on q.id=a.partido_id where a.id=aid and v.version=1 and v.hash_contenido=a.hash_contenido and q.url_acta=a.url_pdf) then raise exception 'Escritura no conciliada'; end if;
  raise exception using errcode='ZX108',message='Reversión obligatoria';
 exception when sqlstate 'ZX108' then rolled:=true;
 end;
 if not rolled or (select to_jsonb(q) from partidos q where q.id=pid) is distinct from original or (select count(*) from actas)<>total_before or (select count(*) from actas_versiones)<>version_before or exists(select 1 from actas where id=aid) or exists(select 1 from actas_versiones where acta_id=aid) then raise exception 'Reversión no confirmada'; end if;
 return jsonb_build_object('acta_escrita',true,'version_escrita',true,'vinculo_verificado',true,'reversion',true,'partido_intacto',true);
end; $$;
revoke all on function public.probar_escritura_acta_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_escritura_acta_m14(jsonb) to service_role;
commit;
