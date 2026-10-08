-- =============================================================================
-- TESADÜF — unlimited concurrent tesadüfs.
-- A user may have any number of live chats at once; a tesadüf ends only by time
-- or by a user. The only rule: the same two people never share two live chats.
-- A waiting user learns about a match made by someone else through
-- match_queue.matched_match_id (picked up on the next poll).
-- =============================================================================

alter table public.match_queue
  add column if not exists matched_match_id uuid references public.matches (id) on delete cascade;

create or replace function public.find_match(p_mode text default 'text') returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_queue public.match_queue;
  v_match public.matches;
  v_candidate uuid;
  v_joined timestamptz;
  v_waiting int;
begin
  if p_mode is null or p_mode <> 'text' then
    raise exception 'UNSUPPORTED_MODE';
  end if;

  -- Serialize all matchmaking decisions; held until commit.
  perform pg_advisory_xact_lock(hashtext('tesaduf:matchmaker'));

  -- Someone matched us while we were waiting: hand that match over.
  select * into v_queue from public.match_queue where user_id = v_me;
  if v_queue.matched_match_id is not null then
    delete from public.match_queue where user_id = v_me;
    v_match := public._settle_match(v_queue.matched_match_id);
    if v_match.id is not null and v_match.status in ('active', 'deciding') then
      return jsonb_build_object('state', 'matched', 'match', public._match_json(v_match, v_me));
    end if;
  end if;

  delete from public.match_queue where last_seen_at < now() - interval '5 minutes';

  insert into public.match_queue as q (user_id, mode)
  values (v_me, p_mode)
  on conflict (user_id) do update
     set last_seen_at = now(),
         matched_match_id = null,
         joined_at = case when q.mode = excluded.mode then q.joined_at else now() end,
         mode = excluded.mode
  returning joined_at into v_joined;

  select q.user_id into v_candidate
    from public.match_queue q
    join public.profiles p on p.id = q.user_id
   where q.mode = p_mode
     and q.user_id <> v_me
     and q.matched_match_id is null
     and q.last_seen_at > now() - public._c_queue_freshness()
     and p.status = 'active'
     and not exists (
       select 1 from public.blocks b
        where (b.blocker_id = v_me and b.blocked_id = q.user_id)
           or (b.blocker_id = q.user_id and b.blocked_id = v_me))
     -- Never a second live chat with the same person.
     and not exists (
       select 1 from public.matches m
        where least(m.user_a, m.user_b) = least(v_me, q.user_id)
          and greatest(m.user_a, m.user_b) = greatest(v_me, q.user_id)
          and m.status in ('active', 'deciding', 'destiny'))
   order by
     -- Prefer people we have never met; fall back to them only if no one else.
     exists (
       select 1 from public.matches m
        where least(m.user_a, m.user_b) = least(v_me, q.user_id)
          and greatest(m.user_a, m.user_b) = greatest(v_me, q.user_id)),
     q.joined_at
   limit 1;

  if v_candidate is null then
    select count(*) into v_waiting
      from public.match_queue
     where mode = p_mode and user_id <> v_me and matched_match_id is null
       and last_seen_at > now() - public._c_queue_freshness();
    return jsonb_build_object(
      'state', 'waiting',
      'waiting_since', v_joined,
      'others_waiting', v_waiting,
      'server_time', now());
  end if;

  insert into public.matches (user_a, user_b, mode, expires_at, decision_deadline)
  values (v_candidate, v_me, p_mode,
          now() + public._c_match_duration(),
          now() + public._c_match_duration() + public._c_decision_window())
  returning * into v_match;

  delete from public.match_queue where user_id = v_me;
  update public.match_queue set matched_match_id = v_match.id where user_id = v_candidate;

  return jsonb_build_object('state', 'matched', 'match', public._match_json(v_match, v_me));
end
$$;

-- Bootstrap also reports how many tesadüfs are running (for the Home reminder).
create or replace function public.ensure_profile() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := public._require_uid();
  v_profile public.profiles;
  v_live public.matches;
  v_id uuid;
  v_live_count int := 0;
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

  for v_id in
    select m.id from public.matches m
     where (m.user_a = v_uid or m.user_b = v_uid) and m.status in ('active', 'deciding')
     order by m.started_at desc
  loop
    v_live := public._settle_match(v_id);
    if v_live.status in ('active', 'deciding') then
      v_live_count := v_live_count + 1;
    end if;
  end loop;

  select * into v_live
    from public.matches m
   where (m.user_a = v_uid or m.user_b = v_uid) and m.status in ('active', 'deciding')
   order by m.started_at desc
   limit 1;

  return jsonb_build_object(
    'profile', public._profile_json(v_profile),
    'stats', public._profile_stats(v_uid),
    'live_match', case when v_live.id is not null then public._match_json(v_live, v_uid) end,
    'live_count', v_live_count,
    'server_time', now()
  );
end
$$;
