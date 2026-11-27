begin;
-- 126: shared document catalogue. This migration never changes competition rules.
create table if not exists public.documentos_fuentes (
 codigo text primary key check(codigo in ('FAR','FER')),
 nombre text not null,
 indice_url text not null check(indice_url ~ '^https://'),
 habilitada boolean not null default true
);
insert into public.documentos_fuentes(codigo,nombre,indice_url) values
 ('FAR','Federacion Andaluza de Rugby','https://farugby.matchready.es/es/public/documentation/1/index/'),
 ('FER','Real Federacion Espanola de Rugby','https://ferugby.es/circulares/')
on conflict(codigo) do nothing;

create table if not exists public.documentos_oficiales (
 id bigint generated always as identity primary key,
 fuente text not null references public.documentos_fuentes(codigo),
 url_oficial text not null,
 titulo text not null check(length(btrim(titulo)) between 1 and 1000),
 tipo text not null check(tipo in ('circular','reglamento','anexo','calendario','otro')),
 categorias jsonb not null default '[]' check(jsonb_typeof(categorias)='array'),
 version_actual integer not null default 0 check(version_actual>=0),
 creada_en timestamptz not null default now(),
 comprobada_en timestamptz not null default now(),
 unique(fuente,url_oficial),
 check((fuente='FAR' and url_oficial ~ '^https://(farugby[.]com|www[.]farugby[.]com|farugby[.]matchready[.]es)/')
 or (fuente='FER' and url_oficial ~ '^https://(www[.])?ferugby[.]es/'))
);
create table if not exists public.documentos_versiones (
 id bigint generated always as identity primary key,
 documento_id bigint not null references public.documentos_oficiales(id),
 version integer not null check(version>0),
 sha256 text not null check(sha256 ~ '^[0-9a-f]{64}$'),
 archivo text not null,
 tamano_bytes integer not null check(tamano_bytes between 1 and 20000000),
 descargada_en timestamptz not null default now(),
 unique(documento_id,version),
 check(archivo=sha256||'.pdf')
);
create table if not exists public.documentos_temporadas (
 documento_id bigint not null references public.documentos_oficiales(id),
 temporada_id bigint not null references public.temporadas(id),
 procedencia text not null check(procedencia in ('temporada_en_fuente','vigencia_por_revisar')),
 reglas_estado text not null default 'pendiente_revision' check(reglas_estado in ('pendiente_revision','verificadas','descartadas')),
 primary key(documento_id,temporada_id)
);
create table if not exists public.documentos_sincronizaciones (
 temporada_id bigint not null references public.temporadas(id),
 fuente text not null references public.documentos_fuentes(codigo),
 estado text not null default 'pendiente' check(estado in ('pendiente','en_curso','actualizada','pendiente_publicacion','error')),
 solicitada_en timestamptz not null default now(),
 revisada_en timestamptz,
 detalle text,
 primary key(temporada_id,fuente)
);
create index if not exists documentos_versiones_hash_idx on public.documentos_versiones(sha256);
create index if not exists documentos_temporadas_temporada_idx on public.documentos_temporadas(temporada_id);

insert into storage.buckets(id,name,public) values('documentos-oficiales','documentos-oficiales',true)
on conflict(id) do nothing;

create or replace function public.encolar_documentos_temporada()
returns trigger language plpgsql security definer set search_path='' as $$
begin
 -- Discover documents only; never copy rules from another season.
 insert into public.documentos_sincronizaciones(temporada_id,fuente)
 select new.id,f.codigo from public.documentos_fuentes f where f.habilitada
 on conflict(temporada_id,fuente) do nothing;
 return new;
end; $$;
revoke all on function public.encolar_documentos_temporada() from public,anon,authenticated;
drop trigger if exists documentos_al_crear_temporada on public.temporadas;
create trigger documentos_al_crear_temporada after insert on public.temporadas
for each row execute function public.encolar_documentos_temporada();
insert into public.documentos_sincronizaciones(temporada_id,fuente)
 select t.id,f.codigo from public.temporadas t cross join public.documentos_fuentes f where f.habilitada
on conflict(temporada_id,fuente) do nothing;

create or replace function public.registrar_documento_oficial(
 p_temporada_id bigint,p_fuente text,p_url text,p_titulo text,p_tipo text,p_categorias jsonb,p_sha256 text,p_tamano integer,
 p_procedencia text default 'temporada_en_fuente'
) returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare doc public.documentos_oficiales%rowtype; old_hash text; next_version integer; changed boolean:=false;
begin
 if p_temporada_id is null or p_fuente is null or p_url is null or p_titulo is null or p_tipo is null or p_procedencia is null
 or p_procedencia not in ('temporada_en_fuente','vigencia_por_revisar')
 or jsonb_typeof(p_categorias) is distinct from 'array' or coalesce(p_sha256,'') !~ '^[0-9a-f]{64}$'
 or p_tamano is null or p_tamano not between 1 and 20000000 then raise exception 'Documento invalido'; end if;
 if not exists(select 1 from temporadas where id=p_temporada_id) then raise exception 'Temporada inexistente'; end if;
 if not exists(select 1 from storage.objects where bucket_id='documentos-oficiales' and name=p_sha256||'.pdf') then raise exception 'PDF no almacenado'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_fuente||':'||p_url,126));
 insert into documentos_oficiales(fuente,url_oficial,titulo,tipo,categorias)
 values(p_fuente,p_url,p_titulo,p_tipo,p_categorias)
 on conflict(fuente,url_oficial) do nothing;
 select * into strict doc from documentos_oficiales where fuente=p_fuente and url_oficial=p_url for update;
 select sha256 into old_hash from documentos_versiones where documento_id=doc.id and version=doc.version_actual;
 next_version:=doc.version_actual;
 if old_hash is distinct from p_sha256 then
  next_version:=doc.version_actual+1;
  insert into documentos_versiones(documento_id,version,sha256,archivo,tamano_bytes)
  values(doc.id,next_version,p_sha256,p_sha256||'.pdf',p_tamano);
  changed:=true;
 end if;
 update documentos_oficiales set titulo=p_titulo,tipo=p_tipo,categorias=p_categorias,version_actual=next_version,comprobada_en=now() where id=doc.id;
 insert into documentos_temporadas(documento_id,temporada_id,procedencia) values(doc.id,p_temporada_id,p_procedencia)
 on conflict(documento_id,temporada_id) do nothing;
 if changed then
  -- A changed document must be reviewed again in every season linked to it.
  update documentos_temporadas set reglas_estado='pendiente_revision' where documento_id=doc.id;
 end if;
 return jsonb_build_object('documento_id',doc.id,'version',next_version,'nueva_version',changed,'reglas_aplicadas',false);
end; $$;
revoke all on function public.registrar_documento_oficial(bigint,text,text,text,text,jsonb,text,integer,text) from public,anon,authenticated;
grant execute on function public.registrar_documento_oficial(bigint,text,text,text,text,jsonb,text,integer,text) to service_role;

alter table public.documentos_fuentes enable row level security;
alter table public.documentos_oficiales enable row level security;
alter table public.documentos_versiones enable row level security;
alter table public.documentos_temporadas enable row level security;
alter table public.documentos_sincronizaciones enable row level security;
drop policy if exists documentos_fuentes_lectura on public.documentos_fuentes;
create policy documentos_fuentes_lectura on public.documentos_fuentes for select to anon,authenticated using(true);
drop policy if exists documentos_oficiales_lectura on public.documentos_oficiales;
create policy documentos_oficiales_lectura on public.documentos_oficiales for select to anon,authenticated using(true);
drop policy if exists documentos_versiones_lectura on public.documentos_versiones;
create policy documentos_versiones_lectura on public.documentos_versiones for select to anon,authenticated using(true);
drop policy if exists documentos_temporadas_lectura on public.documentos_temporadas;
create policy documentos_temporadas_lectura on public.documentos_temporadas for select to anon,authenticated using(true);
revoke all on public.documentos_fuentes,public.documentos_oficiales,public.documentos_versiones,public.documentos_temporadas,public.documentos_sincronizaciones from anon,authenticated;
grant select on public.documentos_fuentes,public.documentos_oficiales,public.documentos_versiones,public.documentos_temporadas to anon,authenticated;
grant all on public.documentos_fuentes,public.documentos_oficiales,public.documentos_versiones,public.documentos_temporadas,public.documentos_sincronizaciones to service_role;
grant usage,select on sequence public.documentos_oficiales_id_seq,public.documentos_versiones_id_seq to service_role;
commit;
