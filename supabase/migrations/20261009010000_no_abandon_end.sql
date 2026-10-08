-- =============================================================================
-- TESADÜF — a tesadüf never ends because of a bad connection.
-- Only time (15 min + decision window) or a user action (end / block / report /
-- "end" decision) closes it. Presence is still shown ("Bağlantısı zayıf").
-- =============================================================================

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
