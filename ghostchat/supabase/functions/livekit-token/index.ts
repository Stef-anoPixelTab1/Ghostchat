// Edge Function: rilascia un token LiveKit solo ai partecipanti di una chiamata.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { AccessToken } from "npm:livekit-server-sdk@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const auth = req.headers.get("Authorization") ?? "";
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: auth } },
  });
  const { data: { user } } = await sb.auth.getUser();
  const { callId } = await req.json().catch(() => ({}));
  // Grazie alla RLS, la riga è visibile solo a chiamante e destinatario.
  const { data: call } = await sb.from("calls").select("id,status").eq("id", callId ?? "").maybeSingle();
  if (!user || !call || call.status !== "accepted") {
    return new Response("forbidden", { status: 403, headers: cors });
  }
  const at = new AccessToken(Deno.env.get("LIVEKIT_API_KEY")!, Deno.env.get("LIVEKIT_API_SECRET")!, {
    identity: user.id,
    ttl: "1h",
  });
  at.addGrant({ roomJoin: true, room: call.id, canPublish: true, canSubscribe: true });
  return new Response(
    JSON.stringify({ token: await at.toJwt(), url: Deno.env.get("LIVEKIT_URL") }),
    { headers: { ...cors, "Content-Type": "application/json" } },
  );
});
