-- =============================================================================
-- TESADÜF — avatar customisation, profile stats, blocked-users management
-- =============================================================================

-- Avatars: "av_<color 1-8>_<shape 1-4>". Legacy "orb_<n>" values stay valid.
alter table public.profiles drop constraint if exists profiles_avatar_check;
alter table public.profiles
  add constraint profiles_avatar_check check (avatar ~ '^(orb_[1-8]|av_[1-8]_[1-4])$');

create or replace function public._profile_json(p public.profiles) returns jsonb
language sql stable security definer set search_path = public, pg_temp as $$
  select jsonb_build_object(
    'anonymous_id', p.anonymous_id,
    'avatar',       p.avatar,
    'created_at',   p.created_at
  )
$$;

-- Tesadüf count, destiny count and number of distinct days with a tesadüf.
create or replace function public._profile_stats(p_me uuid) returns jsonb
language sql stable security definer set search_path = public, pg_temp as $$
  select jsonb_build_object(
    'tesaduf_count', count(*),
    'destiny_count', count(*) filter (where m.destiny_at is not null),
    'active_days',   count(distinct (m.started_at at time zone 'Europe/Istanbul')::date)
  )
  from public.matches m
  where m.user_a = p_me or m.user_b = p_me
$$;

create or replace function public.ensure_profile() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := public._require_uid();
  v_profile public.profiles;
  v_live public.matches;
  v_attempt int := 0;
begin
  select * into v_profile from public.profiles where id = v_uid;

  while v_profile.id is null loop
    v_attempt := v_attempt + 1;
    begin
      insert into public.profiles (id, anonymous_id, avatar)
      values (
        v_uid,
        public._generate_anonymous_id(),
        'av_' || (1 + floor(random() * 8))::int || '_' || (1 + floor(random() * 4))::int)
      on conflict (id) do nothing;
    exception when unique_violation then
      if v_attempt >= 5 then
        raise;
      end if;
    end;
    select * into v_profile from public.profiles where id = v_uid;
  end loop;

  if v_profile.status <> 'active' then
    raise exception 'ACCOUNT_SUSPENDED';
  end if;

  update public.profiles set last_seen_at = now() where id = v_uid;

  select * into v_live
    from public.matches m
   where (m.user_a = v_uid or m.user_b = v_uid) and m.status in ('active', 'deciding')
   order by m.started_at desc
   limit 1;
  if v_live.id is not null then
    v_live := public._settle_match(v_live.id);
  end if;

  return jsonb_build_object(
    'profile', public._profile_json(v_profile),
    'stats', public._profile_stats(v_uid),
    'live_match', case when v_live.id is not null and v_live.status in ('active', 'deciding')
                       then public._match_json(v_live, v_uid) end,
    'server_time', now()
  );
end
$$;

create or replace function public.update_avatar(p_avatar text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_profile public.profiles;
begin
  if p_avatar is null or p_avatar !~ '^av_[1-8]_[1-4]$' then
    raise exception 'INVALID_AVATAR';
  end if;
  update public.profiles
     set avatar = p_avatar, updated_at = now()
   where id = v_me
   returning * into v_profile;
  return public._profile_json(v_profile);
end
$$;

create or replace function public.list_blocks() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id',           b.id,
             'anonymous_id', p.anonymous_id,
             'avatar',       p.avatar,
             'created_at',   b.created_at
           ) order by b.created_at desc)
      from public.blocks b
      join public.profiles p on p.id = b.blocked_id
     where b.blocker_id = v_me
  ), '[]'::jsonb);
end
$$;

create or replace function public.unblock(p_block_id bigint) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_deleted int;
begin
  if p_block_id is null then
    raise exception 'INVALID_BLOCK_ID';
  end if;
  delete from public.blocks where id = p_block_id and blocker_id = v_me;
  get diagnostics v_deleted = row_count;
  return jsonb_build_object('unblocked', v_deleted > 0);
end
$$;

-- New functions get EXECUTE for PUBLIC by default: lock them down again.
revoke execute on function
  public._profile_json(public.profiles),
  public._profile_stats(uuid),
  public.update_avatar(text),
  public.list_blocks(),
  public.unblock(bigint)
from public, anon, authenticated;

grant execute on function
  public.update_avatar(text),
  public.list_blocks(),
  public.unblock(bigint)
to authenticated;
