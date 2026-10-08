-- =============================================================================
-- TESADÜF — initial schema
--
-- Design notes
--  * Every state change goes through SECURITY DEFINER functions that derive the
--    caller from auth.uid(). Clients never write tables directly (no INSERT /
--    UPDATE / DELETE grants, no write policies).
--  * Row Level Security limits direct reads to the caller's own rows. Partner
--    data is only ever exposed through functions, and only as the anonymous id
--    and avatar key — never the partner's auth uuid via the API layer.
--  * Match lifecycle:  active --(expires_at)--> deciding --> destiny | ended
--    Transitions caused by time are applied lazily by _settle_match(), which
--    takes a row lock, so concurrent callers (two phones deciding at the same
--    millisecond) are serialized and see one consistent outcome.
--  * Matchmaking is serialized with a transaction-scoped advisory lock so two
--    concurrent searches can never put the same user into two matches.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Tunables
-- -----------------------------------------------------------------------------
create or replace function public._c_match_duration() returns interval
language sql immutable as $$ select interval '15 minutes' $$;

create or replace function public._c_decision_window() returns interval
language sql immutable as $$ select interval '2 minutes' $$;

-- A participant that has not been seen for this long abandons an active match.
create or replace function public._c_presence_timeout() returns interval
language sql immutable as $$ select interval '3 minutes' $$;

-- "Online" indicator threshold shown to the partner.
create or replace function public._c_online_window() returns interval
language sql immutable as $$ select interval '45 seconds' $$;

-- Queue entries older than this are not matched (client polls every few seconds).
create or replace function public._c_queue_freshness() returns interval
language sql immutable as $$ select interval '20 seconds' $$;

-- -----------------------------------------------------------------------------
-- Tables
-- -----------------------------------------------------------------------------
create table public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  -- 8 chars, unambiguous alphabet (no 0/O/1/I). Shown as #XXXXXXXX.
  anonymous_id  text not null unique check (anonymous_id ~ '^[A-HJ-NP-Z2-9]{8}$'),
  avatar        text not null check (avatar ~ '^orb_[1-8]$'),
  status        text not null default 'active' check (status in ('active', 'suspended')),
  last_seen_at  timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create table public.matches (
  id                 uuid primary key default gen_random_uuid(),
  user_a             uuid not null references public.profiles (id) on delete cascade,
  user_b             uuid not null references public.profiles (id) on delete cascade,
  mode               text not null default 'text' check (mode in ('text', 'voice')),
  status             text not null default 'active'
                       check (status in ('active', 'deciding', 'destiny', 'ended')),
  started_at         timestamptz not null default now(),
  expires_at         timestamptz not null,
  decision_deadline  timestamptz not null,
  user_a_decision    text check (user_a_decision in ('continue', 'end')),
  user_b_decision    text check (user_b_decision in ('continue', 'end')),
  destiny_at         timestamptz,
  ended_at           timestamptz,
  ended_by           uuid references public.profiles (id) on delete set null,
  end_reason         text check (end_reason in
                       ('ended_by_user', 'expired', 'declined', 'blocked', 'reported', 'abandoned')),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint matches_distinct_users check (user_a <> user_b),
  constraint matches_deadline_order check (decision_deadline >= expires_at and expires_at > started_at)
);

create index matches_user_a_idx on public.matches (user_a, started_at desc);
create index matches_user_b_idx on public.matches (user_b, started_at desc);
create index matches_live_a_idx on public.matches (user_a) where status in ('active', 'deciding');
create index matches_live_b_idx on public.matches (user_b) where status in ('active', 'deciding');
create index matches_pair_idx on public.matches (least(user_a, user_b), greatest(user_a, user_b));

create table public.match_queue (
  user_id       uuid primary key references public.profiles (id) on delete cascade,
  mode          text not null default 'text' check (mode in ('text', 'voice')),
  joined_at     timestamptz not null default now(),
  last_seen_at  timestamptz not null default now()
);

create index match_queue_mode_joined_idx on public.match_queue (mode, joined_at);

create table public.messages (
  id          bigint generated always as identity primary key,
  match_id    uuid not null references public.matches (id) on delete cascade,
  sender_id   uuid not null references public.profiles (id) on delete cascade,
  -- Client generated idempotency key: retries of the same send never duplicate.
  client_id   uuid not null,
  body        text not null check (char_length(body) between 1 and 1000),
  created_at  timestamptz not null default now(),
  constraint messages_idempotency unique (match_id, sender_id, client_id)
);

create index messages_match_id_idx on public.messages (match_id, id);
create index messages_sender_recent_idx on public.messages (sender_id, created_at desc);

create table public.blocks (
  id          bigint generated always as identity primary key,
  blocker_id  uuid not null references public.profiles (id) on delete cascade,
  blocked_id  uuid not null references public.profiles (id) on delete cascade,
  created_at  timestamptz not null default now(),
  constraint blocks_unique unique (blocker_id, blocked_id),
  constraint blocks_distinct check (blocker_id <> blocked_id)
);

create index blocks_blocked_idx on public.blocks (blocked_id, blocker_id);

create table public.reports (
  id           bigint generated always as identity primary key,
  reporter_id  uuid not null references public.profiles (id) on delete cascade,
  reported_id  uuid not null references public.profiles (id) on delete cascade,
  match_id     uuid references public.matches (id) on delete set null,
  reason       text not null check (reason in ('spam', 'insult', 'harassment', 'inappropriate', 'other')),
  details      text check (details is null or char_length(details) <= 500),
  status       text not null default 'open' check (status in ('open', 'reviewed', 'dismissed')),
  created_at   timestamptz not null default now(),
  constraint reports_one_per_match unique (reporter_id, match_id)
);

create index reports_reported_idx on public.reports (reported_id, created_at desc);
create index reports_reporter_recent_idx on public.reports (reporter_id, created_at desc);

-- -----------------------------------------------------------------------------
-- Row Level Security: read-only, own rows only. No write policies at all.
-- -----------------------------------------------------------------------------
alter table public.profiles    enable row level security;
alter table public.matches     enable row level security;
alter table public.match_queue enable row level security;
alter table public.messages    enable row level security;
alter table public.blocks      enable row level security;
alter table public.reports     enable row level security;

revoke all on public.profiles, public.matches, public.match_queue,
              public.messages, public.blocks, public.reports from anon, authenticated;
grant select on public.profiles, public.matches, public.messages, public.blocks
  to authenticated;

create policy profiles_select_own on public.profiles
  for select to authenticated using (id = (select auth.uid()));

create policy matches_select_participant on public.matches
  for select to authenticated
  using ((select auth.uid()) in (user_a, user_b));

-- Messages are readable only while the match is open (active / deciding / destiny).
-- Ended tesadüfs disappear for both sides.
create or replace function public._can_read_match(p_match_id uuid) returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1 from public.matches m
    where m.id = p_match_id
      and auth.uid() in (m.user_a, m.user_b)
      and m.status in ('active', 'deciding', 'destiny')
  )
$$;

create policy messages_select_open_match on public.messages
  for select to authenticated using (public._can_read_match(match_id));

create policy blocks_select_own on public.blocks
  for select to authenticated using (blocker_id = (select auth.uid()));

-- match_queue and reports: RLS enabled, no policies => no direct access.

-- -----------------------------------------------------------------------------
-- Internal helpers (not executable by API roles)
-- -----------------------------------------------------------------------------
create or replace function public._require_uid() returns uuid
language plpgsql stable as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v_uid;
end
$$;

-- Returns the caller's id after checking the profile exists and is not suspended.
-- Also refreshes presence.
create or replace function public._require_active_profile() returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := public._require_uid();
  v_status text;
begin
  update public.profiles set last_seen_at = now()
   where id = v_uid
   returning status into v_status;
  if v_status is null then
    raise exception 'PROFILE_NOT_FOUND';
  end if;
  if v_status <> 'active' then
    raise exception 'ACCOUNT_SUSPENDED';
  end if;
  return v_uid;
end
$$;

create or replace function public._is_live(m public.matches) returns boolean
language sql stable as $$
  select (m.status = 'active' and m.expires_at > now())
      or (m.status = 'deciding' and m.decision_deadline > now())
$$;

create or replace function public._partner_of(m public.matches, p_me uuid) returns uuid
language sql immutable as $$
  select case when m.user_a = p_me then m.user_b else m.user_a end
$$;

-- Locks the match row and applies time based transitions. Returns null if missing.
create or replace function public._settle_match(p_match_id uuid) returns public.matches
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  m public.matches;
begin
  select * into m from public.matches where id = p_match_id for update;
  if not found then
    return null;
  end if;

  if m.status = 'active' and now() >= m.expires_at then
    update public.matches
       set status = 'deciding', updated_at = now()
     where id = m.id
     returning * into m;
  end if;

  if m.status = 'active'
     and m.started_at < now() - public._c_presence_timeout()
     and exists (
       select 1 from public.profiles p
        where p.id in (m.user_a, m.user_b)
          and p.last_seen_at < now() - public._c_presence_timeout()
     ) then
    update public.matches
       set status = 'ended', end_reason = 'abandoned', ended_at = now(), updated_at = now()
     where id = m.id
     returning * into m;
  end if;

  if m.status = 'deciding' and now() >= m.decision_deadline then
    update public.matches
       set status = 'ended',
           end_reason = case
             when 'end' in (coalesce(m.user_a_decision, ''), coalesce(m.user_b_decision, ''))
               then 'declined' else 'expired' end,
           ended_at = now(), updated_at = now()
     where id = m.id
     returning * into m;
  end if;

  return m;
end
$$;

-- Locks + settles a match and verifies the caller participates in it.
create or replace function public._load_match_for(p_match_id uuid, p_me uuid) returns public.matches
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  m public.matches;
begin
  if p_match_id is null then
    raise exception 'INVALID_MATCH_ID';
  end if;
  m := public._settle_match(p_match_id);
  if m.id is null or p_me not in (m.user_a, m.user_b) then
    -- Same error for "missing" and "not yours": no existence oracle.
    raise exception 'MATCH_NOT_FOUND';
  end if;
  return m;
end
$$;

create or replace function public._match_json(m public.matches, p_me uuid) returns jsonb
language sql stable security definer set search_path = public, pg_temp as $$
  select jsonb_build_object(
    'id',                m.id,
    'mode',              m.mode,
    'status',            m.status,
    'started_at',        m.started_at,
    'expires_at',        m.expires_at,
    'decision_deadline', m.decision_deadline,
    'destiny_at',        m.destiny_at,
    'ended_at',          m.ended_at,
    'end_reason',        m.end_reason,
    'ended_by_me',       coalesce(m.ended_by = p_me, false),
    'my_decision',       case when m.user_a = p_me then m.user_a_decision else m.user_b_decision end,
    'partner_decided',   (case when m.user_a = p_me then m.user_b_decision else m.user_a_decision end) is not null,
    'partner', jsonb_build_object(
      'anonymous_id', p.anonymous_id,
      'avatar',       p.avatar,
      'online',       p.last_seen_at > now() - public._c_online_window()
    ),
    'server_time',       now()
  )
  from public.profiles p
  where p.id = public._partner_of(m, p_me)
$$;

create or replace function public._message_json(msg public.messages, p_me uuid) returns jsonb
language sql immutable as $$
  select jsonb_build_object(
    'id',         msg.id,
    'match_id',   msg.match_id,
    'client_id',  msg.client_id,
    'mine',       msg.sender_id = p_me,
    'body',       msg.body,
    'created_at', msg.created_at
  )
$$;

create or replace function public._generate_anonymous_id() returns text
language plpgsql volatile as $$
declare
  c_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_id text := '';
begin
  for i in 1..8 loop
    v_id := v_id || substr(c_alphabet, 1 + floor(random() * 32)::int, 1);
  end loop;
  return v_id;
end
$$;

-- Ends a match on behalf of p_me. Caller must hold the row lock (via _load_match_for).
create or replace function public._end_match(m public.matches, p_me uuid, p_reason text)
returns public.matches
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  r public.matches;
begin
  if m.status = 'ended' then
    return m;
  end if;
  update public.matches
     set status = 'ended',
         end_reason = p_reason,
         ended_by = p_me,
         ended_at = now(),
         updated_at = now(),
         user_a_decision = case when m.status = 'deciding' and user_a = p_me
                                then coalesce(user_a_decision, 'end') else user_a_decision end,
         user_b_decision = case when m.status = 'deciding' and user_b = p_me
                                then coalesce(user_b_decision, 'end') else user_b_decision end
   where id = m.id
   returning * into r;
  return r;
end
$$;

-- -----------------------------------------------------------------------------
-- Public API (called by Edge Functions with the user's JWT)
-- -----------------------------------------------------------------------------

-- bootstrap: creates the anonymous profile on first call; idempotent afterwards.
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
      values (v_uid, public._generate_anonymous_id(), 'orb_' || (1 + floor(random() * 8))::int)
      on conflict (id) do nothing;
    exception when unique_violation then
      -- anonymous_id collision (1 in ~10^12): retry with a new one.
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
    'profile', jsonb_build_object(
      'anonymous_id', v_profile.anonymous_id,
      'avatar',       v_profile.avatar,
      'created_at',   v_profile.created_at
    ),
    'live_match', case when v_live.id is not null and v_live.status in ('active', 'deciding')
                       then public._match_json(v_live, v_uid) end,
    'server_time', now()
  );
end
$$;

-- matchmaker (join): either returns an existing live match, creates one with a
-- waiting partner, or (re)queues the caller.
create or replace function public.find_match(p_mode text default 'text') returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_live_id uuid;
  v_match public.matches;
  v_candidate uuid;
  v_joined timestamptz;
  v_waiting int;
begin
  -- Voice is part of the data model but not offered yet.
  if p_mode is null or p_mode <> 'text' then
    raise exception 'UNSUPPORTED_MODE';
  end if;

  -- Serialize all matchmaking decisions; held until commit.
  perform pg_advisory_xact_lock(hashtext('tesaduf:matchmaker'));

  for v_live_id in
    select id from public.matches
     where (user_a = v_me or user_b = v_me) and status in ('active', 'deciding')
  loop
    v_match := public._settle_match(v_live_id);
    if v_match.status in ('active', 'deciding') then
      delete from public.match_queue where user_id = v_me;
      return jsonb_build_object('state', 'matched', 'match', public._match_json(v_match, v_me));
    end if;
  end loop;

  delete from public.match_queue where last_seen_at < now() - interval '5 minutes';

  insert into public.match_queue as q (user_id, mode)
  values (v_me, p_mode)
  on conflict (user_id) do update
     set last_seen_at = now(),
         joined_at = case when q.mode = excluded.mode then q.joined_at else now() end,
         mode = excluded.mode
  returning joined_at into v_joined;

  select q.user_id into v_candidate
    from public.match_queue q
    join public.profiles p on p.id = q.user_id
   where q.mode = p_mode
     and q.user_id <> v_me
     and q.last_seen_at > now() - public._c_queue_freshness()
     and p.status = 'active'
     and not exists (
       select 1 from public.blocks b
        where (b.blocker_id = v_me and b.blocked_id = q.user_id)
           or (b.blocker_id = q.user_id and b.blocked_id = v_me))
     and not exists (
       select 1 from public.matches m
        where (m.user_a = q.user_id or m.user_b = q.user_id)
          and m.status in ('active', 'deciding')
          and public._is_live(m))
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
     where mode = p_mode and user_id <> v_me
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

  delete from public.match_queue where user_id in (v_me, v_candidate);

  return jsonb_build_object('state', 'matched', 'match', public._match_json(v_match, v_me));
end
$$;

create or replace function public.cancel_matchmaking() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_uid();
begin
  delete from public.match_queue where user_id = v_me;
  return jsonb_build_object('state', 'cancelled');
end
$$;

create or replace function public.get_match(p_match_id uuid) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
begin
  return public._match_json(public._load_match_for(p_match_id, v_me), v_me);
end
$$;

-- Presence ping. Optionally returns the (settled) state of the given match.
create or replace function public.heartbeat(p_match_id uuid default null) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
begin
  if p_match_id is null then
    return jsonb_build_object('ok', true, 'server_time', now());
  end if;
  return jsonb_build_object(
    'ok', true,
    'match', public._match_json(public._load_match_for(p_match_id, v_me), v_me),
    'server_time', now());
end
$$;

-- destiny-decision: atomic. The row lock taken in _settle_match serializes
-- concurrent decisions from both phones.
create or replace function public.submit_decision(p_match_id uuid, p_decision text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
  v_is_a boolean;
  v_mine text;
  v_theirs text;
begin
  if p_decision is null or p_decision not in ('continue', 'end') then
    raise exception 'INVALID_DECISION';
  end if;

  m := public._load_match_for(p_match_id, v_me);

  if m.status = 'active' then
    raise exception 'MATCH_NOT_EXPIRED';
  end if;
  if m.status in ('destiny', 'ended') then
    -- Already final; idempotent answer.
    return public._match_json(m, v_me);
  end if;

  v_is_a := m.user_a = v_me;
  v_mine := case when v_is_a then m.user_a_decision else m.user_b_decision end;

  -- First decision is final; a retry with a different value cannot flip it.
  if v_mine is null then
    update public.matches
       set user_a_decision = case when v_is_a then p_decision else user_a_decision end,
           user_b_decision = case when v_is_a then user_b_decision else p_decision end,
           updated_at = now()
     where id = m.id
     returning * into m;
  end if;

  v_mine   := case when v_is_a then m.user_a_decision else m.user_b_decision end;
  v_theirs := case when v_is_a then m.user_b_decision else m.user_a_decision end;

  if v_mine = 'end' or v_theirs = 'end' then
    update public.matches
       set status = 'ended', end_reason = 'declined', ended_at = now(), updated_at = now(),
           ended_by = case when v_mine = 'end' then v_me else public._partner_of(m, v_me) end
     where id = m.id
     returning * into m;
  elsif v_mine = 'continue' and v_theirs = 'continue' then
    update public.matches
       set status = 'destiny', destiny_at = now(), updated_at = now()
     where id = m.id
     returning * into m;
  end if;

  return public._match_json(m, v_me);
end
$$;

create or replace function public.end_match(p_match_id uuid) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
begin
  m := public._load_match_for(p_match_id, v_me);
  m := public._end_match(m, v_me, 'ended_by_user');
  return public._match_json(m, v_me);
end
$$;

create or replace function public.send_message(p_match_id uuid, p_body text, p_client_id uuid)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
  v_body text := btrim(coalesce(p_body, ''));
  v_msg public.messages;
begin
  if char_length(v_body) = 0 or char_length(v_body) > 1000 then
    raise exception 'INVALID_MESSAGE';
  end if;
  if p_client_id is null then
    raise exception 'INVALID_CLIENT_ID';
  end if;

  -- Row lock also serializes inserts per match, so ids follow commit order and
  -- an "after id" cursor never skips a message.
  m := public._load_match_for(p_match_id, v_me);

  -- Retry of an already stored message: return it even if the match moved on.
  select * into v_msg from public.messages
   where match_id = m.id and sender_id = v_me and client_id = p_client_id;
  if v_msg.id is not null then
    return public._message_json(v_msg, v_me);
  end if;

  if not (m.status = 'destiny' or (m.status = 'active' and now() < m.expires_at)) then
    raise exception 'MATCH_NOT_OPEN';
  end if;

  if (select count(*) from public.messages
       where sender_id = v_me and created_at > now() - interval '10 seconds') >= 15 then
    raise exception 'RATE_LIMITED';
  end if;

  insert into public.messages (match_id, sender_id, client_id, body)
  values (m.id, v_me, p_client_id, v_body)
  returning * into v_msg;

  return public._message_json(v_msg, v_me);
end
$$;

-- messages: p_after = 0 returns the latest p_limit messages; otherwise only newer ones.
create or replace function public.list_messages(p_match_id uuid, p_after bigint default 0, p_limit int default 100)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
  v_limit int := least(greatest(coalesce(p_limit, 100), 1), 200);
  v_after bigint := greatest(coalesce(p_after, 0), 0);
  v_messages jsonb;
begin
  m := public._load_match_for(p_match_id, v_me);

  if m.status in ('active', 'deciding', 'destiny') then
    select coalesce(jsonb_agg(public._message_json(x, v_me) order by x.id), '[]'::jsonb)
      into v_messages
      from (
        select * from public.messages
         where match_id = m.id and id > v_after
         order by case when v_after = 0 then -id else id end
         limit v_limit
      ) x;
  else
    v_messages := '[]'::jsonb;
  end if;

  return jsonb_build_object('match', public._match_json(m, v_me), 'messages', v_messages);
end
$$;

create or replace function public.my_chats(p_limit int default 30) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_limit int := least(greatest(coalesce(p_limit, 30), 1), 100);
begin
  return coalesce((
    select jsonb_agg(row_json order by started_at desc)
      from (
        select m.started_at,
               jsonb_build_object(
                 'id',          m.id,
                 'mode',        m.mode,
                 -- Effective status without taking locks on every row.
                 'status',      case
                                  when m.status = 'active' and now() >= m.decision_deadline then 'ended'
                                  when m.status = 'active' and now() >= m.expires_at then 'deciding'
                                  when m.status = 'deciding' and now() >= m.decision_deadline then 'ended'
                                  else m.status end,
                 'started_at',  m.started_at,
                 'ended_at',    m.ended_at,
                 'end_reason',  m.end_reason,
                 'is_destiny',  m.destiny_at is not null,
                 'partner', jsonb_build_object('anonymous_id', p.anonymous_id, 'avatar', p.avatar),
                 'last_message', case when m.status in ('active', 'deciding', 'destiny') then (
                   select jsonb_build_object('body', left(x.body, 80), 'mine', x.sender_id = v_me,
                                             'created_at', x.created_at)
                     from public.messages x where x.match_id = m.id
                    order by x.id desc limit 1) end
               ) as row_json
          from public.matches m
          join public.profiles p on p.id = public._partner_of(m, v_me)
         where m.user_a = v_me or m.user_b = v_me
         order by m.started_at desc
         limit v_limit
      ) s
  ), '[]'::jsonb);
end
$$;

create or replace function public.block_user(p_match_id uuid) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
begin
  m := public._load_match_for(p_match_id, v_me);
  insert into public.blocks (blocker_id, blocked_id)
  values (v_me, public._partner_of(m, v_me))
  on conflict (blocker_id, blocked_id) do nothing;
  m := public._end_match(m, v_me, 'blocked');
  return jsonb_build_object('blocked', true, 'match', public._match_json(m, v_me));
end
$$;

-- Reporting also blocks the partner and ends the tesadüf: safety first.
create or replace function public.report_user(p_match_id uuid, p_reason text, p_details text default null)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
  v_partner uuid;
  v_details text := nullif(btrim(coalesce(p_details, '')), '');
begin
  if p_reason is null or p_reason not in ('spam', 'insult', 'harassment', 'inappropriate', 'other') then
    raise exception 'INVALID_REASON';
  end if;
  if v_details is not null and char_length(v_details) > 500 then
    raise exception 'INVALID_DETAILS';
  end if;
  if (select count(*) from public.reports
       where reporter_id = v_me and created_at > now() - interval '1 day') >= 20 then
    raise exception 'RATE_LIMITED';
  end if;

  m := public._load_match_for(p_match_id, v_me);
  v_partner := public._partner_of(m, v_me);

  insert into public.reports (reporter_id, reported_id, match_id, reason, details)
  values (v_me, v_partner, m.id, p_reason, v_details)
  on conflict (reporter_id, match_id) do nothing;

  insert into public.blocks (blocker_id, blocked_id)
  values (v_me, v_partner)
  on conflict (blocker_id, blocked_id) do nothing;

  m := public._end_match(m, v_me, 'reported');
  return jsonb_build_object('reported', true, 'blocked', true, 'match', public._match_json(m, v_me));
end
$$;

-- -----------------------------------------------------------------------------
-- Privileges: only the API functions are callable, and only when signed in
-- (Supabase anonymous users have the "authenticated" role).
-- -----------------------------------------------------------------------------
revoke execute on all functions in schema public from public, anon, authenticated;

grant execute on function
  public.ensure_profile(),
  public.find_match(text),
  public.cancel_matchmaking(),
  public.get_match(uuid),
  public.heartbeat(uuid),
  public.submit_decision(uuid, text),
  public.end_match(uuid),
  public.send_message(uuid, text, uuid),
  public.list_messages(uuid, bigint, int),
  public.my_chats(int),
  public.block_user(uuid),
  public.report_user(uuid, text, text)
to authenticated;

-- Needed by the messages RLS policy (evaluated as the caller).
grant execute on function public._can_read_match(uuid) to authenticated;

-- Realtime: clients subscribe to inserts on their match's messages and updates
-- on the match row as a "something changed" signal (RLS applies).
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.messages, public.matches;
  end if;
end
$$;
