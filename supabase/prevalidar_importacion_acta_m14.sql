begin;
-- Phase 104: transactional admission gate, deliberately no writes.
create or replace function public.prevalidar_importacion_acta_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare pid bigint; p record; a record; k text; counts jsonb:='{}'; marcador jsonb; n integer;
begin
 if jsonb_typeof(p_acta) is distinct from 'object' then raise exception 'Paquete inválido'; end if;
 pid:=(p_acta->>'partido')::bigint;
 if pid is null or coalesce(p_acta->>'acta','') !~ '^[0-9]+$' or coalesce(p_acta->>'hash_contenido','') !~ '^[0-9a-f]{64}$' then raise exception 'Identificador o huella inválidos'; end if;
 if p_acta->>'url' is distinct from ('https://farugby.matchready.es/es/public/competition/match_pdf/'||(p_acta->>'acta')||'/act/') then raise exception 'URL inválida'; end if;
 marcador:=p_acta->'marcador';
 if jsonb_typeof(marcador) is distinct from 'array' then raise exception 'Marcador inválido'; end if;
 if jsonb_array_length(marcador)<>2 or coalesce(marcador->>0,'') !~ '^[0-9]+$' or coalesce(marcador->>1,'') !~ '^[0-9]+$' then raise exception 'Marcador inválido'; end if;
 lock table public.jornadas,public.partidos in share mode;
 lock table public.actas,public.alineaciones,public.anotaciones,public.sustituciones,public.sanciones,public.personal_partidos in share mode;
 select p0.* into p from partidos p0 join jornadas j on j.id=p0.jornada_id join competiciones c on c.id=j.competicion_id where p0.id=pid and c.id_oficial in ('35/11','35/12','35/15');
 if not found then raise exception 'Partido fuera de alcance'; end if;
 foreach k in array array['alineaciones','anotaciones','sustituciones','sanciones','participaciones','personal'] loop
  if jsonb_typeof(p_acta->k) is distinct from 'array' then raise exception 'Sección inválida %',k; end if;
  n:=jsonb_array_length(p_acta->k);
  if n>500 then raise exception 'Sección excesiva'; end if;
  counts:=counts||jsonb_build_object(k,n);
 end loop;
 if (counts->>'alineaciones')::int=0 or counts->>'alineaciones' is distinct from counts->>'participaciones' then raise exception 'Cobertura inválida'; end if;
 select id,hash_contenido into a from actas where partido_id=pid;
 if found then
  if a.hash_contenido is distinct from p_acta->>'hash_contenido' or p.url_acta is distinct from p_acta->>'url' or jsonb_build_array(p.puntos_local,p.puntos_visitante) is distinct from marcador then raise exception 'Acta existente discrepante'; end if;
  return jsonb_build_object('estado','existente','registros',counts,'escritura',false);
 end if;
 if p.url_acta is not null or exists(select 1 from alineaciones where partido_id=pid) or exists(select 1 from anotaciones where partido_id=pid) or exists(select 1 from sustituciones where partido_id=pid) or exists(select 1 from sanciones where partido_id=pid) or exists(select 1 from personal_partidos where partido_id=pid) then raise exception 'Partido con datos previos'; end if;
 if jsonb_build_array(p.puntos_local,p.puntos_visitante) not in ('[null,null]'::jsonb,marcador) then raise exception 'Marcador discrepante'; end if;
 if p.estado::text not in ('pendiente','finalizado') then raise exception 'Estado no admitido'; end if;
 return jsonb_build_object('estado','candidato','registros',counts,'escritura',false);
end; $$;
revoke all on function public.prevalidar_importacion_acta_m14(jsonb) from public,anon,authenticated;
grant execute on function public.prevalidar_importacion_acta_m14(jsonb) to service_role;
commit;
