begin;
create or replace function public.probar_importacion_integral_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare p record; j1 bigint;  a1 bigint; a2 bigint; bc bigint[]; aid bigint; jid bigint; original jsonb; attempt integer; late_failure boolean:=false; rolled boolean:=false;
begin
 lock table jornadas,partidos,actas,alineaciones,anotaciones,sustituciones,sanciones,personal_partidos,participaciones,actas_versiones,jugadores,jugadores_equipos in share row exclusive mode;
 if prevalidar_importacion_acta_m14(p_acta)->>'estado'<>'existente' then raise exception 'Origen no confirmado'; end if;
 select q.* into p from partidos q join jornadas j on j.id=q.jornada_id join competiciones c on c.id=j.competicion_id
 where c.id_oficial in ('35/11','35/12','35/15') and q.estado='pendiente' and q.puntos_local is null and q.puntos_visitante is null and q.url_acta is null
 and not exists(select 1 from actas t where t.partido_id=q.id)
 and not exists(select 1 from alineaciones t where t.partido_id=q.id)
 and not exists(select 1 from anotaciones t where t.partido_id=q.id)
 and not exists(select 1 from sustituciones t where t.partido_id=q.id)
 and not exists(select 1 from sanciones t where t.partido_id=q.id)
 and not exists(select 1 from personal_partidos t where t.partido_id=q.id)
 order by q.id limit 1;
 if not found then raise exception 'Sin partido de prueba'; end if;
 select id into j1 from jugadores where licencia=(p_acta->'alineaciones'->0->>'licencia');
 if j1 is null then raise exception 'Identidades invalidas'; end if;
 original:=to_jsonb(p);
 bc:=array[(select count(*) from actas),(select count(*) from actas_versiones),(select count(*) from jugadores),(select count(*) from jugadores_equipos),(select count(*) from alineaciones),(select count(*) from anotaciones),(select count(*) from sustituciones),(select count(*) from sanciones),(select count(*) from participaciones),(select count(*) from personal_partidos)];
 for attempt in 1..2 loop
 rolled:=false; late_failure:=false;
 begin
 insert into jugadores(nombre_completo) values('Prueba integral reversible 112') returning id into jid;
 insert into jugadores_equipos(jugador_id,equipo_id) values(jid,p.equipo_local_id);
 insert into actas(partido_id,url_pdf,hash_contenido,version,datos,estado_validacion,descargada_en,comprobada_en,version_procesador)
 values(p.id,p_acta->>'url',p_acta->>'hash_contenido',1,jsonb_build_object('prueba',true),'pendiente',now(),now(),'112-prueba') returning id into aid;
 insert into actas_versiones(acta_id,version,hash_contenido,datos,motivo)
 values(aid,1,p_acta->>'hash_contenido',jsonb_build_object('prueba',true),'Prueba integral reversible');
 update partidos set puntos_local=14,puntos_visitante=0,estado='finalizado',url_acta=p_acta->>'url' where id=p.id;
 insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_local_id,j1,1,true) returning id into a1;
 insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_local_id,jid,2,false) returning id into a2;
 insert into anotaciones(partido_id,equipo_id,jugador_id,dorsal,minuto,orden_evento,tipo,puntos,verificada) values
 (p.id,p.equipo_local_id,j1,1,2,1,'ensayo',5,true),(p.id,p.equipo_local_id,j1,1,3,2,'transformacion',2,true),(p.id,p.equipo_local_id,null,null,4,3,'ensayo_castigo',7,true);
 insert into sustituciones(partido_id,equipo_id,jugador_entra_id,jugador_sale_id,dorsal_entra,dorsal_sale,minuto,orden_evento,verificada)
 values(p.id,p.equipo_local_id,jid,j1,2,1,10,1,true);
 insert into sanciones(partido_id,equipo_id,jugador_id,dorsal,minuto,orden_evento,tipo,duracion_reglamentaria,minuto_fin,verificada)
 values(p.id,p.equipo_local_id,j1,1,5,1,'temporal',5,10,true);
 insert into participaciones(alineacion_id,jugado,minutos_jugados,minutos_sancionado,estado_calculo) values(a1,true,31,5,'reglamentario'),(a2,false,0,0,'reglamentario');
 insert into personal_partidos(partido_id,equipo_id,nombre_completo,licencia,funcion) values(p.id,null,'Arbitro ficticio','PRUEBA112','arbitro'),(p.id,p.equipo_local_id,'Entrenador ficticio','PRUEBA112-E','entrenador');
 if (select count(*) from participaciones where alineacion_id in(a1,a2))<>2 or (select sum(minutos_jugados) from participaciones where alineacion_id in(a1,a2))<>31 or (select sum(minutos_sancionado) from participaciones where alineacion_id in(a1,a2))<>5 or (select count(*) from personal_partidos where partido_id=p.id)<>2 then raise exception 'Datos no conciliados'; end if;
 if (select sum(puntos) from anotaciones where partido_id=p.id)<>14
 or (select count(*) from sustituciones where partido_id=p.id)<>1
 or (select count(*) from sanciones where partido_id=p.id)<>1
 or not exists(select 1 from actas a join actas_versiones v on v.acta_id=a.id where a.id=aid and v.hash_contenido=a.hash_contenido)
 then raise exception 'Integracion incompleta'; end if;
 if attempt=1 then
 late_failure:=true;
 -- A real constraint failure at the last section must undo every earlier write.
 insert into participaciones(alineacion_id,jugado,minutos_jugados,minutos_sancionado,estado_calculo) values(a1,true,31,5,'reglamentario');
 raise exception 'Duplicado permitido';
 end if;
 raise exception using errcode='ZX112',message='Reversion de ejecucion completa';
 exception
 when unique_violation then
   if attempt<>1 or not late_failure then raise; end if;
   rolled:=true;
 when sqlstate 'ZX112' then
   if attempt<>2 then raise; end if;
   rolled:=true;
 end;
 if not rolled or bc<>array[(select count(*) from actas),(select count(*) from actas_versiones),(select count(*) from jugadores),(select count(*) from jugadores_equipos),(select count(*) from alineaciones),(select count(*) from anotaciones),(select count(*) from sustituciones),(select count(*) from sanciones),(select count(*) from participaciones),(select count(*) from personal_partidos)]
 or (select to_jsonb(q) from partidos q where q.id=p.id) is distinct from original
 or exists(select 1 from jugadores where id=jid)
 or exists(select 1 from actas where partido_id=p.id)
 or exists(select 1 from alineaciones where partido_id=p.id)
 or exists(select 1 from anotaciones where partido_id=p.id)
 or exists(select 1 from sustituciones where partido_id=p.id)
 or exists(select 1 from sanciones where partido_id=p.id)
 or exists(select 1 from personal_partidos where partido_id=p.id)
 then raise exception 'Reversion integral no confirmada'; end if;
 end loop;
 return jsonb_build_object('participaciones',2,'minutos_jugados',31,'minutos_sancionado',5,'personal',2,'fallo_final_revertido',true,'ejecucion_completa_revertida',true,'tablas_verificadas',11,'reversion',true);
end; $$;
revoke all on function public.probar_importacion_integral_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_importacion_integral_m14(jsonb) to service_role;
commit;
