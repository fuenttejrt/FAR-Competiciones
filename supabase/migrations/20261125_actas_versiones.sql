BEGIN;
CREATE OR REPLACE FUNCTION public.actualizar_version_acta(
 p_partido_id bigint, p_version_esperada integer, p_hash_anterior text,
 p_hash_nuevo text, p_url_pdf text, p_estado text, p_datos jsonb
) RETURNS integer
LANGUAGE plpgsql SECURITY INVOKER SET search_path = ''
AS $$
DECLARE a public.actas%ROWTYPE;
BEGIN
 IF p_hash_nuevo IS NULL OR p_hash_nuevo !~ '^[0-9a-f]{64}$'
 OR p_estado IS NULL OR p_estado NOT IN ('correcto','parcial','pendiente','error')
 OR p_datos IS NULL OR jsonb_typeof(p_datos) <> 'object'
 THEN RAISE EXCEPTION 'Datos de versión inválidos'; END IF;
 SELECT * INTO STRICT a FROM public.actas WHERE partido_id=p_partido_id FOR UPDATE;
 IF a.hash_contenido = p_hash_nuevo THEN RETURN a.version; END IF;
 IF a.version IS DISTINCT FROM p_version_esperada OR a.hash_contenido IS DISTINCT FROM p_hash_anterior
 THEN RAISE EXCEPTION 'Conflicto de versión'; END IF;
 INSERT INTO public.actas_versiones(acta_id,version,hash_contenido,datos,motivo)
 VALUES(a.id,a.version,a.hash_contenido,
 jsonb_build_object('datos',a.datos,'url_pdf',a.url_pdf,'estado_validacion',a.estado_validacion,'version_procesador',a.version_procesador,'comprobada_en',a.comprobada_en),
 'Cambio de contenido oficial');
 UPDATE public.actas SET version=a.version+1,hash_contenido=p_hash_nuevo,url_pdf=p_url_pdf,
 datos=p_datos,estado_validacion='pendiente',descargada_en=now(),comprobada_en=NULL,actualizada_en=now()
 WHERE id=a.id;
 RETURN a.version+1;
END;
$$;
REVOKE ALL ON FUNCTION public.actualizar_version_acta(bigint,integer,text,text,text,text,jsonb) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_version_acta(bigint,integer,text,text,text,text,jsonb) TO service_role;
COMMIT;
