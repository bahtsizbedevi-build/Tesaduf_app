-- =============================================================================
-- TESADÜF — read receipts, message reactions, offensive-language flag.
-- =============================================================================

alter table public.matches
  add column if not exists user_a_last_read bigint not null default 0,
  add column if not exists user_b_last_read bigint not null default 0;

alter table public.messages
  add column if not exists reaction text check (reaction in ('heart', 'laugh', 'wow', 'sad', 'fire')),
  add column if not exists flagged boolean not null default false;

-- Rough Turkish profanity / slur detector. Flagged messages are delivered but shown
-- blurred to the recipient (tap to reveal). Not a moderation verdict.
create or replace function public._is_offensive(p_body text) returns boolean
language sql immutable as $$
  select lower(translate(p_body, 'İIÇĞÖŞÜ', 'iıçğöşü')) ~
    '(^|[^a-zçğıöşü])(amk|aq|amq|mk|oç|orospu[a-zçğıöşü]*|piç[a-zçğıöşü]*|siktir[a-zçğıöşü]*|sik(er|im|eyim|ik|iş)[a-zçğıöşü]*|yarra[a-zçğıöşü]*|amın[a-zçğıöşü]*|pezevenk[a-zçğıöşü]*|kahpe[a-zçğıöşü]*|şerefsiz[a-zçğıöşü]*|gavat[a-zçğıöşü]*|ibne[a-zçğıöşü]*|yavşak[a-zçğıöşü]*|godoş[a-zçğıöşü]*|dalyarak[a-zçğıöşü]*|götveren[a-zçğıöşü]*)([^a-zçğıöşü]|$)'
$$;

create or replace function public._message_json(msg public.messages, p_me uuid) returns jsonb
language sql immutable as $$
  select jsonb_build_object(
    'id',         msg.id,
    'match_id',   msg.match_id,
    'client_id',  msg.client_id,
    'mine',       msg.sender_id = p_me,
    'body',       msg.body,
    'created_at', msg.created_at,
    'reaction',   msg.reaction,
    'flagged',    msg.flagged
  )
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
    'partner_last_read', case when m.user_a = p_me then m.user_b_last_read else m.user_a_last_read end,
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

  m := public._load_match_for(p_match_id, v_me);

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

  insert into public.messages (match_id, sender_id, client_id, body, flagged)
  values (m.id, v_me, p_client_id, v_body, public._is_offensive(v_body))
  returning * into v_msg;

  return public._message_json(v_msg, v_me);
end
$$;

-- Read receipt: only moves forward, capped at the newest message, no-op otherwise
-- (so it never spams realtime updates).
create or replace function public.mark_read(p_match_id uuid, p_last_id bigint) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  m public.matches;
  v_cap bigint;
  v_target bigint;
begin
  m := public._load_match_for(p_match_id, v_me);
  select coalesce(max(id), 0) into v_cap from public.messages where match_id = m.id;
  v_target := least(greatest(coalesce(p_last_id, 0), 0), v_cap);
  if m.user_a = v_me and v_target > m.user_a_last_read then
    update public.matches set user_a_last_read = v_target where id = m.id;
  elsif m.user_b = v_me and v_target > m.user_b_last_read then
    update public.matches set user_b_last_read = v_target where id = m.id;
  end if;
  return jsonb_build_object('last_read', v_target);
end
$$;

-- Reactions: only the recipient of a message can react; null clears it.
create or replace function public.react_message(p_message_id bigint, p_reaction text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_msg public.messages;
  m public.matches;
begin
  if p_reaction is not null and p_reaction not in ('heart', 'laugh', 'wow', 'sad', 'fire') then
    raise exception 'INVALID_REACTION';
  end if;
  select * into v_msg from public.messages where id = p_message_id;
  if v_msg.id is null then
    raise exception 'MATCH_NOT_FOUND';
  end if;
  m := public._load_match_for(v_msg.match_id, v_me);
  if v_msg.sender_id = v_me then
    raise exception 'INVALID_REACTION';
  end if;
  if m.status not in ('active', 'deciding', 'destiny') then
    raise exception 'MATCH_NOT_OPEN';
  end if;
  update public.messages set reaction = p_reaction where id = v_msg.id returning * into v_msg;
  return public._message_json(v_msg, v_me);
end
$$;

revoke execute on function
  public._is_offensive(text),
  public.mark_read(uuid, bigint),
  public.react_message(bigint, text)
from public, anon, authenticated;
grant execute on function public.mark_read(uuid, bigint), public.react_message(bigint, text) to authenticated;
