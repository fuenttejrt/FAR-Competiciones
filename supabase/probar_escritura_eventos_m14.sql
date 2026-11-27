begin;
create or replace function public.probar_escritura_eventos_m14(p_acta jsonb)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare p record; v jsonb; a record; b record; na int:=0; nc int:=0; ns int:=0; before_counts bigint[]; rolled boolean:=false;
begin
 lock table jornadas,partidos,actas,alineaciones,anotaciones,sustituciones,sanciones,personal_partidos in share row exclusive mode;
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
 select id into a from jugadores where licencia=(p_acta->'alineaciones'->0->>'licencia');
 select id into b from jugadores where licencia=(p_acta->'alineaciones'->1->>'licencia');
 if a.id is null or b.id is null or a.id=b.id then raise exception 'Identidades de prueba invalidas'; end if;
 before_counts:=array[(select count(*) from alineaciones),(select count(*) from anotaciones),(select count(*) from sustituciones),(select count(*) from sanciones)];
 begin
 insert into alineaciones(partido_id,equipo_id,jugador_id,dorsal,titular) values(p.id,p.equipo_local_id,a.id,1,true),(p.id,p.equipo_local_id,b.id,2,false);
 insert into anotaciones(partido_id,equipo_id,jugador_id,dorsal,minuto,orden_evento,tipo,puntos,verificada) values
 (p.id,p.equipo_local_id,a.id,1,2,1,'ensayo',5,true),
 (p.id,p.equipo_local_id,a.id,1,3,2,'transformacion',2,true),
 (p.id,p.equipo_local_id,null,null,4,3,'ensayo_castigo',7,true);
 insert into sustituciones(partido_id,equipo_id,jugador_entra_id,jugador_sale_id,dorsal_entra,dorsal_sale,minuto,orden_evento,verificada) values(p.id,p.equipo_local_id,b.id,a.id,2,1,10,1,true);
 insert into sanciones(partido_id,equipo_id,jugador_id,dorsal,minuto,orden_evento,tipo,duracion_reglamentaria,minuto_fin,verificada) values
 (p.id,p.equipo_local_id,b.id,2,12,1,'temporal',5,17,true),
 (p.id,p.equipo_local_id,b.id,2,20,2,'definitiva',null,null,true);
 select count(*) into na from anotaciones where partido_id=p.id;
 select count(*) into nc from sustituciones where partido_id=p.id;
 select count(*) into ns from sanciones where partido_id=p.id;
 if na<>3 or nc<>1 or ns<>2 or (select sum(puntos) from anotaciones where partido_id=p.id)<>14 then raise exception 'Eventos no conciliados'; end if;
 begin
 insert into anotaciones(partido_id,equipo_id,orden_evento,tipo,puntos) values(p.id,p.equipo_local_id,1,'ensayo',5);
 raise exception 'Duplicado permitido';
 exception when unique_violation then null;
 end;
 raise exception using errcode='ZX110',message='Reversion obligatoria';
 exception when sqlstate 'ZX110' then rolled:=true;
 end;
 if not rolled or before_counts<>array[(select count(*) from alineaciones),(select count(*) from anotaciones),(select count(*) from sustituciones),(select count(*) from sanciones)] or exists(select 1 from alineaciones where partido_id=p.id) or exists(select 1 from anotaciones where partido_id=p.id) or exists(select 1 from sustituciones where partido_id=p.id) or exists(select 1 from sanciones where partido_id=p.id) then raise exception 'Reversion no confirmada'; end if;
 return jsonb_build_object('anotaciones',na,'puntos',14,'sustituciones',nc,'sanciones',ns,'duplicado_rechazado',true,'reversion',true);
end; $$;
revoke all on function public.probar_escritura_eventos_m14(jsonb) from public,anon,authenticated;
grant execute on function public.probar_escritura_eventos_m14(jsonb) to service_role;
commit;
