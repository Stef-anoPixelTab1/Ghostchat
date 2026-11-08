-- Ghostchat: esegui questo file nel SQL Editor di Supabase.
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  tag text not null unique check (tag ~ '^[a-z0-9_]{3,20}$'),
  display_name text,
  avatar_url text,
  created_at timestamptz not null default now()
);
create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now()
);
create table public.members (
  conversation_id uuid references public.conversations(id) on delete cascade,
  user_id uuid references public.profiles(id) on delete cascade,
  archived boolean not null default false,
  primary key (conversation_id, user_id)
);
create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null default 'text' check (kind in ('text','voice')),
  body text,
  media_url text,
  duration_ms int,
  font_size real not null default 16 check (font_size between 8 and 48),
  created_at timestamptz not null default now()
);
create index on public.messages (conversation_id, created_at);
create table public.calls (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  caller_id uuid not null references public.profiles(id) on delete cascade,
  callee_id uuid not null references public.profiles(id) on delete cascade,
  video boolean not null default false,
  status text not null default 'ringing' check (status in ('ringing','accepted','declined','ended','missed')),
  created_at timestamptz not null default now()
);

create or replace function public.is_member(cid uuid) returns boolean
language sql security definer set search_path = public stable as $$
  select exists(select 1 from public.members where conversation_id = cid and user_id = auth.uid())
$$;

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.members enable row level security;
alter table public.messages enable row level security;
alter table public.calls enable row level security;

create policy "profiles read" on public.profiles for select to authenticated using (true);
create policy "profiles insert own" on public.profiles for insert to authenticated with check (id = auth.uid());
create policy "profiles update own" on public.profiles for update to authenticated using (id = auth.uid());
create policy "conv read" on public.conversations for select to authenticated using (public.is_member(id));
create policy "members read" on public.members for select to authenticated using (public.is_member(conversation_id));
create policy "members update own" on public.members for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "messages read" on public.messages for select to authenticated using (public.is_member(conversation_id));
create policy "messages insert" on public.messages for insert to authenticated with check (sender_id = auth.uid() and public.is_member(conversation_id));
create policy "calls read" on public.calls for select to authenticated using (caller_id = auth.uid() or callee_id = auth.uid());
create policy "calls insert" on public.calls for insert to authenticated with check (caller_id = auth.uid() and public.is_member(conversation_id));
create policy "calls update" on public.calls for update to authenticated using (caller_id = auth.uid() or callee_id = auth.uid());

-- Avvia (o riapre) una chat 1:1 partendo dal tag dell'altro utente.
create or replace function public.start_chat(other_tag text) returns uuid
language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); other uuid; cid uuid;
begin
  if me is null then raise exception 'not_authenticated'; end if;
  select id into other from public.profiles where tag = lower(other_tag);
  if other is null then raise exception 'user_not_found'; end if;
  if other = me then raise exception 'cannot_chat_self'; end if;
  select m1.conversation_id into cid from public.members m1
    join public.members m2 on m1.conversation_id = m2.conversation_id
    where m1.user_id = me and m2.user_id = other limit 1;
  if cid is null then
    insert into public.conversations default values returning id into cid;
    insert into public.members(conversation_id, user_id) values (cid, me), (cid, other);
  end if;
  return cid;
end $$;
revoke all on function public.start_chat(text) from public;
grant execute on function public.start_chat(text) to authenticated;

-- Realtime
alter table public.calls replica identity full;
alter publication supabase_realtime add table public.messages, public.calls;

-- Storage (foto profilo e vocali)
insert into storage.buckets (id, name, public) values ('avatars','avatars',true), ('voice','voice',true) on conflict (id) do nothing;
create policy "media read" on storage.objects for select to authenticated using (bucket_id in ('avatars','voice'));
create policy "media insert own" on storage.objects for insert to authenticated
  with check (bucket_id in ('avatars','voice') and (storage.foldername(name))[1] = auth.uid()::text);
create policy "media update own" on storage.objects for update to authenticated
  using (bucket_id in ('avatars','voice') and (storage.foldername(name))[1] = auth.uid()::text);
