-- =============================================================================
-- TESADÜF — avatar = random colour (server assigned, fixed) + chosen accessory.
--   "av_<color 1-8>_<accessory 1-6>"  (1 = none, 2 = beanie, 3 = cap,
--   4 = party hat, 5 = crown, 6 = headphones)
-- =============================================================================

alter table public.profiles drop constraint if exists profiles_avatar_check;
alter table public.profiles
  add constraint profiles_avatar_check check (avatar ~ '^(orb_[1-8]|av_[1-8]_[1-6])$');

-- Only the accessory may change; the colour stays the one TESADÜF picked at random.
create or replace function public.update_avatar(p_avatar text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_profile public.profiles;
  v_color text;
begin
  if p_avatar is null or p_avatar !~ '^av_[1-8]_[1-6]$' then
    raise exception 'INVALID_AVATAR';
  end if;
  select * into v_profile from public.profiles where id = v_me;
  v_color := coalesce(substring(v_profile.avatar from '^av_([1-8])_'),
                      substring(v_profile.avatar from '^orb_([1-8])$'));
  if v_color is null or split_part(p_avatar, '_', 2) <> v_color then
    raise exception 'INVALID_AVATAR';
  end if;
  update public.profiles
     set avatar = p_avatar, updated_at = now()
   where id = v_me
   returning * into v_profile;
  return public._profile_json(v_profile);
end
$$;

-- New profiles: random colour, no accessory.
create or replace function public._new_avatar() returns text
language sql volatile as $$
  select 'av_' || (1 + floor(random() * 8))::int || '_1'
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
      values (v_uid, public._generate_anonymous_id(), public._new_avatar())
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

revoke execute on function public._new_avatar() from public, anon, authenticated;
