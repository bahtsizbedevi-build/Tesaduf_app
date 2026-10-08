-- =============================================================================
-- TESADÜF — more accessories: 7 glasses (free), 8 cat ears (10 tesadüf),
-- 9 halo (100 messages sent).
-- =============================================================================

alter table public.profiles drop constraint if exists profiles_avatar_check;
alter table public.profiles
  add constraint profiles_avatar_check check (avatar ~ '^(orb_[1-8]|av_[1-8]_[1-9])$');

create or replace function public._unlocked_accessories(p_total int, p_kader int, p_best int, p_msgs int) returns jsonb
language sql immutable as $$
  select '[1,2,3,7]'::jsonb
    || case when p_total >= 5 then '[4]'::jsonb else '[]'::jsonb end
    || case when p_kader >= 1 then '[5]'::jsonb else '[]'::jsonb end
    || case when p_best >= 3 then '[6]'::jsonb else '[]'::jsonb end
    || case when p_total >= 10 then '[8]'::jsonb else '[]'::jsonb end
    || case when p_msgs >= 100 then '[9]'::jsonb else '[]'::jsonb end
$$;

create or replace function public._profile_stats(p_me uuid) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare
  v_total int;
  v_kader int;
  v_days int;
  v_msgs int;
  v_night boolean;
  v_streak int := 0;
  v_best int := 0;
  v_today date := (now() at time zone 'Europe/Istanbul')::date;
  v_badges jsonb := '[]'::jsonb;
begin
  select count(*), count(*) filter (where destiny_at is not null),
         count(distinct (started_at at time zone 'Europe/Istanbul')::date),
         bool_or(extract(hour from started_at at time zone 'Europe/Istanbul') between 0 and 4)
    into v_total, v_kader, v_days, v_night
    from public.matches where user_a = p_me or user_b = p_me;
  select count(*) into v_msgs from public.messages where sender_id = p_me;

  with d as (
    select distinct (started_at at time zone 'Europe/Istanbul')::date as day
      from public.matches where user_a = p_me or user_b = p_me
  ), g as (
    select day, day - (row_number() over (order by day))::int as grp from d
  ), runs as (
    select max(day) as last_day, count(*)::int as len from g group by grp
  )
  select coalesce(max(len), 0),
         coalesce(max(len) filter (where last_day >= v_today - 1), 0)
    into v_best, v_streak
    from runs;

  if v_total >= 1 then v_badges := v_badges || '"first_tesaduf"'; end if;
  if v_kader >= 1 then v_badges := v_badges || '"first_kader"'; end if;
  if v_total >= 10 then v_badges := v_badges || '"ten_tesaduf"'; end if;
  if coalesce(v_night, false) then v_badges := v_badges || '"night_owl"'; end if;
  if v_msgs >= 100 then v_badges := v_badges || '"chatty"'; end if;
  if v_best >= 3 then v_badges := v_badges || '"streak_3"'; end if;
  if v_kader >= 5 then v_badges := v_badges || '"kader_5"'; end if;

  return jsonb_build_object(
    'tesaduf_count', v_total,
    'destiny_count', v_kader,
    'active_days',   v_days,
    'messages_sent', v_msgs,
    'streak',        v_streak,
    'best_streak',   v_best,
    'badges',        v_badges,
    'unlocked_accessories', public._unlocked_accessories(v_total, v_kader, v_best, v_msgs)
  );
end
$$;

create or replace function public.update_avatar(p_avatar text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_me uuid := public._require_active_profile();
  v_profile public.profiles;
  v_color text;
  v_acc int;
begin
  if p_avatar is null or p_avatar !~ '^av_[1-8]_[1-9]$' then
    raise exception 'INVALID_AVATAR';
  end if;
  select * into v_profile from public.profiles where id = v_me;
  v_color := coalesce(substring(v_profile.avatar from '^av_([1-8])_'),
                      substring(v_profile.avatar from '^orb_([1-8])$'));
  if v_color is null or split_part(p_avatar, '_', 2) <> v_color then
    raise exception 'INVALID_AVATAR';
  end if;
  v_acc := split_part(p_avatar, '_', 3)::int;
  if p_avatar <> v_profile.avatar
     and not (public._profile_stats(v_me) -> 'unlocked_accessories') @> to_jsonb(v_acc) then
    raise exception 'ACCESSORY_LOCKED';
  end if;
  update public.profiles
     set avatar = p_avatar, updated_at = now()
   where id = v_me
   returning * into v_profile;
  return public._profile_json(v_profile);
end
$$;

revoke execute on function public._unlocked_accessories(int, int, int, int) from public, anon, authenticated;
