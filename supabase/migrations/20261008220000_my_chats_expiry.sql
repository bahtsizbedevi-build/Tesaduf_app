-- =============================================================================
-- TESADÜF — my_chats also returns the server expiry so the Chats list can show a
-- live countdown for running tesadüfs (instead of a static "Aktif" badge).
-- =============================================================================

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
                 'id',                m.id,
                 'mode',              m.mode,
                 -- Effective status without taking locks on every row.
                 'status',            case
                                        when m.status = 'active' and now() >= m.decision_deadline then 'ended'
                                        when m.status = 'active' and now() >= m.expires_at then 'deciding'
                                        when m.status = 'deciding' and now() >= m.decision_deadline then 'ended'
                                        else m.status end,
                 'started_at',        m.started_at,
                 'expires_at',        m.expires_at,
                 'decision_deadline', m.decision_deadline,
                 'ended_at',          m.ended_at,
                 'end_reason',        m.end_reason,
                 'is_destiny',        m.destiny_at is not null,
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
