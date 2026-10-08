-- =============================================================================
-- TESADÜF — lightweight moderation for app admins.
-- Admins are listed in public.admins (managed from the dashboard / SQL only).
-- They see open reports with the reported user's anonymous id and the last few
-- messages of the reported match, and can suspend / dismiss.
-- =============================================================================

create table if not exists public.admins (
  user_id    uuid primary key references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.admins enable row level security;
revoke all on public.admins from anon, authenticated;

create or replace function public._require_admin() returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
begin
  if not exists (select 1 from public.admins where user_id = v_me) then
    raise exception 'FORBIDDEN';
  end if;
  return v_me;
end
$$;

create or replace function public.admin_reports(p_status text default 'open') returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_admin();
begin
  if p_status not in ('open', 'reviewed', 'dismissed') then
    raise exception 'INVALID_STATUS';
  end if;
  return coalesce((
    select jsonb_agg(row order by created_at desc)
      from (
        select r.created_at,
               jsonb_build_object(
                 'id',              r.id,
                 'reason',          r.reason,
                 'details',         r.details,
                 'status',          r.status,
                 'created_at',      r.created_at,
                 'reported_id',     rp.anonymous_id,
                 'reported_avatar', rp.avatar,
                 'reported_status', rp.status,
                 'reporter_id',     ep.anonymous_id,
                 'times_reported',  (select count(*) from public.reports x where x.reported_id = r.reported_id),
                 'evidence', coalesce((
                    select jsonb_agg(jsonb_build_object(
                             'from_reported', mm.sender_id = r.reported_id,
                             'body', left(mm.body, 300),
                             'created_at', mm.created_at) order by mm.id)
                      from (select * from public.messages
                             where match_id = r.match_id order by id desc limit 8) mm
                 ), '[]'::jsonb)
               ) as row
          from public.reports r
          join public.profiles rp on rp.id = r.reported_id
          join public.profiles ep on ep.id = r.reporter_id
         where r.status = p_status
         order by r.created_at desc
         limit 100
      ) s
  ), '[]'::jsonb);
end
$$;

create or replace function public.admin_resolve(p_report_id bigint, p_action text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_admin();
  r public.reports;
begin
  if p_action not in ('suspend', 'dismiss') then
    raise exception 'INVALID_ACTION';
  end if;
  select * into r from public.reports where id = p_report_id for update;
  if r.id is null then
    raise exception 'NOT_FOUND';
  end if;
  if p_action = 'suspend' then
    update public.profiles set status = 'suspended', updated_at = now() where id = r.reported_id;
    -- Close every report about this user and end their live tesadüfs.
    update public.reports set status = 'reviewed' where reported_id = r.reported_id and status = 'open';
    update public.matches
       set status = 'ended', end_reason = 'reported', ended_at = now(), updated_at = now()
     where (user_a = r.reported_id or user_b = r.reported_id) and status in ('active', 'deciding', 'destiny');
    delete from public.match_queue where user_id = r.reported_id;
  else
    update public.reports set status = 'dismissed' where id = r.id;
  end if;
  return jsonb_build_object('ok', true);
end
$$;

-- Bootstrap tells the app whether to show the moderation entry.
create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (select 1 from public.admins where user_id = auth.uid())
$$;

revoke execute on function public._require_admin(), public.admin_reports(text),
  public.admin_resolve(bigint, text), public.is_admin() from public, anon, authenticated;
grant execute on function public.admin_reports(text), public.admin_resolve(bigint, text), public.is_admin() to authenticated;
