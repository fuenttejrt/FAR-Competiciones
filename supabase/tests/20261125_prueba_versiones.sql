BEGIN;
DO $$
DECLARE a public.actas%ROWTYPE;
 b public.actas%ROWTYPE;
 h text;
 v integer;
 conflicto boolean := false;
BEGIN
 SELECT * INTO STRICT a FROM public.actas ORDER BY id LIMIT 1 FOR UPDATE;
 h := CASE WHEN a.hash_contenido=repeat('a',64) THEN repeat('b',64) ELSE repeat('a',64) END;
 v := public.actualizar_version_acta(a.partido_id,a.version,a.hash_contenido,h,a.url_pdf,'correcto','{"prueba":true}'::jsonb);
 SELECT * INTO STRICT b FROM public.actas WHERE id=a.id;
 IF v<>a.version+1 OR b.version<>v OR b.hash_contenido IS DISTINCT FROM h OR b.estado_validacion IS DISTINCT FROM 'pendiente' THEN
 RAISE EXCEPTION 'Error de actualización'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.actas_versiones WHERE acta_id=a.id AND version=a.version AND hash_contenido=a.hash_contenido AND datos->'datos'=a.datos) THEN
 RAISE EXCEPTION 'Histórico incorrecto'; END IF;
 IF public.actualizar_version_acta(a.partido_id,a.version,a.hash_contenido,h,a.url_pdf,'correcto','{"prueba":true}'::jsonb)<>v THEN
 RAISE EXCEPTION 'Repetición incorrecta'; END IF;
 BEGIN
 PERFORM public.actualizar_version_acta(a.partido_id,a.version,a.hash_contenido,repeat('c',64),a.url_pdf,'correcto','{}'::jsonb);
 EXCEPTION WHEN raise_exception THEN
 IF SQLERRM='Conflicto de versión' THEN conflicto:=true; ELSE RAISE; END IF;
 END;
 IF NOT conflicto THEN RAISE EXCEPTION 'Conflicto no detectado'; END IF;
 IF (SELECT version FROM public.actas WHERE id=a.id)<>v THEN RAISE EXCEPTION 'Versión alterada'; END IF;
 RAISE NOTICE 'OK: histórico, versión siguiente, repetición, conflicto y estado pendiente';
END $$;
ROLLBACK;
SELECT 'Prueba superada; cambios revertidos' AS resultado, count(*) AS actas FROM public.actas;
