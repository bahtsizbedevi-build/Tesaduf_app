-- =============================================================================
-- TESADÜF — FCM push tokens.
-- Clients register their device token; the send-message Edge Function looks up
-- the recipient's tokens with the service role (never exposed to apps).
-- =============================================================================

create table if not exists public.push_tokens (
  token      text primary key check (char_length(token) between 20 and 4096),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens (user_id);
alter table public.push_tokens enable row level security;
revoke all on public.push_tokens from anon, authenticated;

-- A device token belongs to whoever registered it last (handles sign-out / new identity).
create or replace function public.register_push_token(p_token text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
begin
  if p_token is null or char_length(p_token) not between 20 and 4096 then
    raise exception 'INVALID_TOKEN';
  end if;
  insert into public.push_tokens (token, user_id) values (p_token, v_me)
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
  return jsonb_build_object('ok', true);
end
$$;

create or replace function public.unregister_push_token(p_token text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_uid();
begin
  delete from public.push_tokens where token = p_token and user_id = v_me;
  return jsonb_build_object('ok', true);
end
$$;

-- Server only: who should be notified about a message, and what to show.
create or replace function public.push_targets_for_message(p_message_id bigint) returns jsonb
language sql stable security definer set search_path = public, pg_temp as $$
  select jsonb_build_object(
    'match_id',  msg.match_id,
    'sender_id', sp.anonymous_id,
    'body',      case when msg.flagged then null else left(msg.body, 140) end,
    'tokens',    coalesce((select jsonb_agg(t.token) from public.push_tokens t
                            where t.user_id = public._partner_of(m, msg.sender_id)), '[]'::jsonb)
  )
  from public.messages msg
  join public.matches m on m.id = msg.match_id
  join public.profiles sp on sp.id = msg.sender_id
  where msg.id = p_message_id
$$;

create or replace function public.drop_push_tokens(p_tokens text[]) returns void
language sql security definer set search_path = public, pg_temp as $$
  delete from public.push_tokens where token = any (p_tokens)
$$;

revoke execute on function public.register_push_token(text), public.unregister_push_token(text),
  public.push_targets_for_message(bigint), public.drop_push_tokens(text[]) from public, anon, authenticated;
grant execute on function public.register_push_token(text), public.unregister_push_token(text) to authenticated;
grant execute on function public.push_targets_for_message(bigint), public.drop_push_tokens(text[]) to service_role;
