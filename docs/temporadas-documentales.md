# Preparación de nuevas temporadas documentales (129)

Estado: código preparado; requiere instalar la migración y conectar la app antes de utilizarlo.

- La app 0.128 continúa conservando sus carpetas locales. No solicita todavía temporadas al servidor.
- `supabase/preparar_temporada_documental.sql` define un registro idempotente por nombre y encola las fuentes habilitadas FAR y FER.
- No cambia la temporada deportiva activa, no crea categorías ni competiciones y no copia reglas de otra temporada.
- El descargador existente revisará las temporadas registradas en sus ejecuciones programadas; la descarga no es inmediata.
- Si las fuentes aún no publican una temporada, el sincronizador la marcará pendiente de publicación.
- La función queda accesible únicamente al servicio. La futura conexión móvil requerirá un punto de acceso limitado; nunca se debe incluir la clave privada en la APK.
- Prueba reversible preparada en `supabase/tests/20261127_temporadas_documentales.sql`; no ejecutada en Supabase.
- No se crea ninguna temporada nueva al instalar la migración.
